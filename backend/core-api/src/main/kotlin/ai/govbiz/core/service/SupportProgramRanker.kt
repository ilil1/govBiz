package ai.govbiz.core.service

import ai.govbiz.core.domain.support.SupportProgram
import ai.govbiz.core.domain.support.SupportProgramStatus
import ai.govbiz.core.text.isBlankLikeJava
import ai.govbiz.core.text.trimLikeJava
import org.springframework.stereotype.Component
import java.text.Normalizer
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

/** 검증된 AI 검색 의도로 공고를 필터링하고 관련도와 추천 이유를 계산합니다. */
@Component
class SupportProgramRanker {
    fun rank(
        candidates: List<CatalogSupportProgram>,
        analyzedIntent: AnalyzedSearchIntent?,
        acceptingOnly: Boolean,
        limit: Int,
    ): List<SupportProgram> {
        require(limit > 0) { "limit must be positive" }
        val intent = analyzedIntent?.let(SearchIntent::from) ?: SearchIntent.empty()

        return candidates
            .asSequence()
            .filter { !acceptingOnly || it.program.status == SupportProgramStatus.OPEN }
            .map { score(it, intent) }
            .filter { intent.terms.isEmpty() || it.score > 0 }
            .sortedWith(
                compareByDescending<ScoredProgram> { it.score }
                    .thenByDescending { it.candidate.sortTimestamp },
            )
            .take(limit)
            .map(::withMatchedReasons)
            .toList()
    }

    private fun score(candidate: CatalogSupportProgram, intent: SearchIntent): ScoredProgram {
        if (intent.terms.isEmpty()) return ScoredProgram(candidate, 1, emptyList())

        var score = 0
        var regionMatched = false
        val matches = mutableListOf<QueryTerm>()
        for (term in intent.terms) {
            if (term.kind == TermKind.REGION && regionMatched) continue
            val termScore = scoreTerm(candidate.program, term)
            if (termScore > 0) {
                score += termScore
                matches += term
                if (term.kind == TermKind.REGION) regionMatched = true
            }
        }
        return ScoredProgram(candidate, score, java.util.List.copyOf(matches))
    }

    private fun scoreTerm(program: SupportProgram, term: QueryTerm): Int {
        if (term.kind == TermKind.REGION &&
            ("전국" in program.regions || term.label in program.regions)
        ) {
            return 12
        }
        if (term.kind == TermKind.CATEGORY &&
            program.categories.any { normalize(it) == normalize(term.label) }
        ) {
            return 11
        }
        if (term.kind == TermKind.TARGET &&
            term.variants.any { containsSearchTerm(program.targetDescription, it) }
        ) {
            return 8
        }

        var best = 0
        for (variant in term.variants) {
            if (variant.isBlankLikeJava()) continue
            if (containsSearchTerm(program.title, variant)) best = max(best, 9)
            if (program.categories.any { containsSearchTerm(it, variant) }) best = max(best, 7)
            if (program.regions.any { containsSearchTerm(it, variant) }) best = max(best, 6)
            if (containsSearchTerm(program.targetDescription, variant)) best = max(best, 4)
            if (containsSearchTerm(program.summary, variant)) best = max(best, 3)
            if (containsSearchTerm(program.organization, variant)) best = max(best, 2)
        }
        return best
    }

    private fun withMatchedReasons(scored: ScoredProgram): SupportProgram {
        val program = scored.candidate.program
        val reasons = LinkedHashSet<String>()
        for (term in scored.matches) {
            reasons += when (term.kind) {
                TermKind.REGION -> "${term.label} 지역"
                TermKind.CATEGORY -> "${term.label} 분야"
                TermKind.TARGET -> "지원대상 ‘${term.label}’"
                TermKind.TEXT -> "‘${term.label}’ 관련"
            }
            if (reasons.size == MAX_REASONS) break
        }
        if (program.status == SupportProgramStatus.OPEN && reasons.size < MAX_REASONS) {
            reasons += "현재 접수 중"
        }
        if (reasons.isEmpty()) reasons += "기업마당 공식 공고"
        return program.copy(matchedReasons = java.util.List.copyOf(reasons))
    }

    private enum class TermKind {
        REGION,
        CATEGORY,
        TARGET,
        TEXT,
    }

    private data class QueryTerm(
        val label: String,
        val variants: List<String>,
        val kind: TermKind,
    )

    private data class SearchIntent(val terms: List<QueryTerm>) {
        companion object {
            fun empty(): SearchIntent = SearchIntent(emptyList())

            fun from(analyzed: AnalyzedSearchIntent): SearchIntent {
                val terms = LinkedHashMap<String, QueryTerm>()
                analyzed.regions.forEach {
                    addTerm(terms, QueryTerm(it, listOf(it), TermKind.REGION))
                }
                analyzed.categories.forEach {
                    addTerm(terms, QueryTerm(it, listOf(it), TermKind.CATEGORY))
                }
                analyzed.targetTerms.forEach {
                    addTerm(terms, QueryTerm(it, listOf(it), TermKind.TARGET))
                }
                analyzed.keywords.forEach {
                    addTerm(terms, QueryTerm(it, listOf(it), TermKind.TEXT))
                }
                return SearchIntent(java.util.List.copyOf(terms.values))
            }

            private fun addTerm(terms: LinkedHashMap<String, QueryTerm>, term: QueryTerm) {
                terms.putIfAbsent("${term.kind.name}:${normalize(term.label)}", term)
            }
        }
    }

    private data class ScoredProgram(
        val candidate: CatalogSupportProgram,
        val score: Int,
        val matches: List<QueryTerm>,
    )

    private companion object {
        const val MAX_REASONS = 3
        val SEARCH_TOKEN_SEPARATOR: Pattern = Pattern.compile("[^\\p{L}\\p{N}&]+")
        val ASCII_SEARCH_TERM: Pattern = Pattern.compile("[a-z0-9&]+")
        val WHITESPACE: Pattern = Pattern.compile("\\s+")

        fun containsSearchTerm(value: String, term: String): Boolean {
            val normalizedTerm = normalize(term)
            if (normalizedTerm.isBlankLikeJava()) return false
            val normalizedValue = normalize(value)
            if (ASCII_SEARCH_TERM.matcher(normalizedTerm).matches()) {
                return SEARCH_TOKEN_SEPARATOR.split(normalizedValue).any(normalizedTerm::equals)
            }
            return normalizedTerm in normalizedValue
        }

        fun normalize(value: String): String {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
            return WHITESPACE.matcher(normalized).replaceAll(" ").trimLikeJava()
        }
    }
}
