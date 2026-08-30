package ai.govbiz.core.service

import ai.govbiz.core.client.bizinfo.BizInfoClient
import ai.govbiz.core.client.bizinfo.BizInfoClientException
import ai.govbiz.core.client.bizinfo.BizInfoProgramPayload
import ai.govbiz.core.domain.support.SupportProgram
import ai.govbiz.core.domain.support.SupportProgramStatus
import ai.govbiz.core.text.isBlankLikeJava
import ai.govbiz.core.text.isNullOrBlankLikeJava
import ai.govbiz.core.text.trimLikeJava
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils
import java.net.URI
import java.net.URISyntaxException
import java.text.Normalizer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

@Service
class SupportProgramSearchService(
    private val client: BizInfoClient,
    private val aiSearchIntentService: AiSearchIntentService,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {
    private val cacheLock = Any()

    @Volatile
    private var cache: CatalogCache? = null

    fun search(rawQuery: String?, acceptingOnly: Boolean): SupportProgramSearchResult {
        val query = rawQuery?.trimLikeJava().orEmpty()
        val intent = if (query.isBlankLikeJava()) {
            SearchIntent.empty()
        } else {
            SearchIntent.from(aiSearchIntentService.analyze(query, acceptingOnly))
        }

        val scored = catalog()
            .asSequence()
            .filter { !acceptingOnly || it.program.status == SupportProgramStatus.OPEN }
            .map { score(it, intent) }
            .filter { intent.terms.isEmpty() || it.score > 0 }
            .sortedWith(
                compareByDescending<ScoredProgram> { it.score }
                    .thenByDescending { it.candidate.sortTimestamp },
            )
            .take(RESULT_LIMIT)
            .toList()

        return SupportProgramSearchResult(
            query = query,
            programs = java.util.List.copyOf(scored.map(::withMatchedReasons)),
        )
    }

    private fun catalog(): List<IndexedProgram> {
        val now = clock.instant()
        var current = cache
        if (current != null && current.fetchedAt.plus(CACHE_TTL).isAfter(now)) {
            return current.programs
        }

        return synchronized(cacheLock) {
            current = cache
            val cached = current
            if (cached != null && cached.fetchedAt.plus(CACHE_TTL).isAfter(now)) {
                return@synchronized cached.programs
            }

            try {
                val refreshed = mapAndDeduplicate(client.fetchAll())
                cache = CatalogCache(refreshed, now)
                refreshed
            } catch (exception: BizInfoClientException) {
                val stale = current
                if (stale != null && stale.fetchedAt.plus(MAX_STALE_AGE).isAfter(now)) {
                    stale.programs
                } else {
                    throw SupportProgramSearchException.fromClient(exception)
                }
            }
        }
    }

    private fun mapAndDeduplicate(payloads: List<BizInfoProgramPayload?>): List<IndexedProgram> {
        val programs = LinkedHashMap<String, IndexedProgram>()
        for (payload in payloads) {
            val program = toIndexedProgram(payload) ?: continue
            programs.putIfAbsent(program.program.id, program)
        }
        return java.util.List.copyOf(programs.values)
    }

    private fun toIndexedProgram(payload: BizInfoProgramPayload?): IndexedProgram? {
        val id = payload?.id?.takeUnless { it.isBlankLikeJava() } ?: return null
        val title = payload.title?.takeUnless { it.isBlankLikeJava() } ?: return null
        val sourceUrl = officialSourceUrl(payload.sourceUrl) ?: return null

        val applicationPeriod = firstPresent(payload.applicationPeriod, "정보 없음")
        val dates = parseDates(applicationPeriod)
        val status = determineStatus(applicationPeriod, dates, LocalDate.now(clock))
        val categories = categories(payload.category)
        val regions = regions(payload.hashtags)
        val summary = plainText(payload.summaryHtml)
        val organization = firstPresent(
            payload.executingOrganization,
            payload.jurisdictionOrganization,
            "정보 없음",
        )

        val program = SupportProgram(
            id = id.trimLikeJava(),
            title = title.trimLikeJava(),
            organization = organization,
            summary = if (summary.isBlankLikeJava()) "정보 없음" else summary,
            categories = categories,
            regions = regions,
            targetDescription = firstPresent(payload.target, "정보 없음"),
            supportAmount = "정보 없음",
            applicationPeriod = applicationPeriod,
            applicationStartDate = dates.start,
            applicationEndDate = dates.end,
            status = status,
            sourceName = "기업마당",
            sourceUrl = sourceUrl,
            matchedReasons = emptyList(),
        )

        val hashtags = normalize(payload.hashtags)
        val searchable = normalize(
            listOf(
                program.title,
                program.organization,
                program.summary,
                program.targetDescription,
                program.categories.joinToString(" "),
                program.regions.joinToString(" "),
                textOrEmpty(payload.hashtags),
                textOrEmpty(payload.applicationMethod),
            ).joinToString(" "),
        )
        return IndexedProgram(
            program = program,
            title = normalize(program.title),
            categories = normalize(categories.joinToString(" ")),
            regions = normalize(regions.joinToString(" ")),
            target = normalize(program.targetDescription),
            summary = normalize(program.summary),
            organization = normalize(program.organization),
            hashtags = hashtags,
            searchable = searchable,
            sortTimestamp = firstPresent(payload.updatedAt, payload.createdAt, ""),
        )
    }

    private fun score(candidate: IndexedProgram, intent: SearchIntent): ScoredProgram {
        if (intent.terms.isEmpty()) return ScoredProgram(candidate, 1, emptyList())

        var score = 0
        var regionMatched = false
        val matches = mutableListOf<QueryTerm>()
        for (term in intent.terms) {
            if (term.kind == TermKind.REGION && regionMatched) continue

            val termScore = scoreTerm(candidate, term)
            if (termScore > 0) {
                score += termScore
                matches += term
                if (term.kind == TermKind.REGION) regionMatched = true
            }
        }
        return ScoredProgram(candidate, score, java.util.List.copyOf(matches))
    }

    private fun scoreTerm(candidate: IndexedProgram, term: QueryTerm): Int {
        if (term.kind == TermKind.REGION &&
            ("전국" in candidate.program.regions || term.label in candidate.program.regions)
        ) {
            return 12
        }
        if (term.kind == TermKind.CATEGORY &&
            candidate.program.categories.any { normalize(it) == normalize(term.label) }
        ) {
            return 11
        }
        if (term.kind == TermKind.TARGET &&
            term.variants.asSequence()
                .any { containsSearchTerm(candidate.target, it) }
        ) {
            return 8
        }

        var best = 0
        for (variant in term.variants) {
            if (variant.isBlankLikeJava()) continue

            if (containsSearchTerm(candidate.title, variant)) best = max(best, 9)
            if (containsSearchTerm(candidate.categories, variant)) best = max(best, 7)
            if (containsSearchTerm(candidate.regions, variant) ||
                containsSearchTerm(candidate.hashtags, variant)
            ) {
                best = max(best, 6)
            }
            if (containsSearchTerm(candidate.target, variant)) best = max(best, 4)
            if (containsSearchTerm(candidate.summary, variant)) best = max(best, 3)
            if (containsSearchTerm(candidate.organization, variant) ||
                containsSearchTerm(candidate.searchable, variant)
            ) {
                best = max(best, 2)
            }
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
            if (reasons.size == 3) break
        }
        if (program.status == SupportProgramStatus.OPEN && reasons.size < 3) {
            reasons += "현재 접수 중"
        }
        if (reasons.isEmpty()) reasons += "기업마당 공식 공고"

        return program.copy(
            matchedReasons = java.util.List.copyOf(reasons),
        )
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
                for (region in analyzed.regions) {
                    addTerm(terms, QueryTerm(region, listOf(region), TermKind.REGION))
                }
                for (category in analyzed.categories) {
                    addTerm(terms, QueryTerm(category, listOf(category), TermKind.CATEGORY))
                }
                for (targetTerm in analyzed.targetTerms) {
                    addTerm(terms, QueryTerm(targetTerm, listOf(targetTerm), TermKind.TARGET))
                }
                for (keyword in analyzed.keywords) {
                    addTerm(terms, QueryTerm(keyword, listOf(keyword), TermKind.TEXT))
                }
                return SearchIntent(java.util.List.copyOf(terms.values))
            }

            fun addTerm(terms: LinkedHashMap<String, QueryTerm>, term: QueryTerm) {
                terms.putIfAbsent(termKey(term), term)
            }

            fun termKey(term: QueryTerm): String =
                "${term.kind.name}:${normalize(term.label)}"
        }
    }

    private data class DateRange(val start: LocalDate?, val end: LocalDate?)

    private data class IndexedProgram(
        val program: SupportProgram,
        val title: String,
        val categories: String,
        val regions: String,
        val target: String,
        val summary: String,
        val organization: String,
        val hashtags: String,
        val searchable: String,
        val sortTimestamp: String,
    )

    private data class ScoredProgram(
        val candidate: IndexedProgram,
        val score: Int,
        val matches: List<QueryTerm>,
    )

    private data class CatalogCache(
        val programs: List<IndexedProgram>,
        val fetchedAt: Instant,
    )

    private companion object {
        const val RESULT_LIMIT = 5
        val CACHE_TTL: Duration = Duration.ofHours(1)
        val MAX_STALE_AGE: Duration = Duration.ofHours(24)
        val ISO_DATE: Pattern = Pattern.compile("\\d{4}[-./]\\d{2}[-./]\\d{2}")
        val HTML_BLOCK: Pattern = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>")
        val HTML_BREAK: Pattern = Pattern.compile("(?i)<br\\s*/?>|</p>|</li>")
        val HTML_TAG: Pattern = Pattern.compile("(?s)<[^>]*>")
        val SEARCH_TOKEN_SEPARATOR: Pattern = Pattern.compile("[^\\p{L}\\p{N}&]+")
        val ASCII_SEARCH_TERM: Pattern = Pattern.compile("[a-z0-9&]+")
        val CATEGORY_SEPARATOR: Pattern = Pattern.compile("[,/·>]")
        val WHITESPACE: Pattern = Pattern.compile("\\s+")

        val SOURCE_REGION_ALIASES = mapOf(
            "서울" to "서울", "서울특별시" to "서울",
            "부산" to "부산", "부산광역시" to "부산",
            "대구" to "대구", "대구광역시" to "대구",
            "인천" to "인천", "인천광역시" to "인천",
            "광주" to "광주", "광주광역시" to "광주",
            "대전" to "대전", "대전광역시" to "대전",
            "울산" to "울산", "울산광역시" to "울산",
            "세종" to "세종", "세종특별자치시" to "세종",
            "경기" to "경기", "경기도" to "경기",
            "강원" to "강원", "강원특별자치도" to "강원",
            "충북" to "충북", "충청북도" to "충북",
            "충남" to "충남", "충청남도" to "충남",
            "전북" to "전북", "전북특별자치도" to "전북",
            "전남" to "전남", "전라남도" to "전남",
            "경북" to "경북", "경상북도" to "경북",
            "경남" to "경남", "경상남도" to "경남",
            "제주" to "제주", "제주특별자치도" to "제주",
            "전국" to "전국",
        )

        fun parseDates(applicationPeriod: String): DateRange {
            val dates = ArrayList<LocalDate>(2)
            val matcher = ISO_DATE.matcher(applicationPeriod)
            while (matcher.find() && dates.size < 2) {
                try {
                    dates += LocalDate.parse(
                        matcher.group().replace('.', '-').replace('/', '-'),
                    )
                } catch (_: DateTimeParseException) {
                    // Invalid upstream date text remains visible through applicationPeriod.
                }
            }
            if (dates.size >= 2) return DateRange(dates[0], dates[1])
            if (dates.size == 1) {
                val normalized = normalize(applicationPeriod)
                if ("까지" in normalized && !isRollingPeriod(normalized)) {
                    return DateRange(null, dates[0])
                }
                if ("부터" in normalized || isRollingPeriod(normalized)) {
                    return DateRange(dates[0], null)
                }
            }
            return DateRange(null, null)
        }

        fun determineStatus(
            applicationPeriod: String,
            dates: DateRange,
            today: LocalDate,
        ): SupportProgramStatus {
            if (dates.start != null && today.isBefore(dates.start)) {
                return SupportProgramStatus.UPCOMING
            }
            if (dates.end != null && today.isAfter(dates.end)) {
                return SupportProgramStatus.CLOSED
            }
            if (dates.start != null && dates.end != null) return SupportProgramStatus.OPEN

            val normalized = normalize(applicationPeriod)
            if (isUpcomingPeriod(normalized)) return SupportProgramStatus.UPCOMING
            if (isRollingPeriod(normalized)) return SupportProgramStatus.OPEN
            if (dates.end != null) return SupportProgramStatus.OPEN
            if (isExplicitlyClosed(normalized)) return SupportProgramStatus.CLOSED
            return SupportProgramStatus.UNKNOWN
        }

        fun isRollingPeriod(normalizedPeriod: String): Boolean = containsAny(
            normalizedPeriod,
            "예산 소진", "예산소진", "상시", "선착순", "모집 완료시", "모집완료시",
            "모집 마감시", "모집마감시", "수시", "정원 마감", "정원마감",
            "규모 마감", "규모마감", "소진시", "완료시",
        )

        fun isUpcomingPeriod(normalizedPeriod: String): Boolean =
            containsAny(normalizedPeriod, "추후 공지", "추후공지", "접수 예정", "접수예정")

        fun isExplicitlyClosed(normalizedPeriod: String): Boolean =
            containsAny(normalizedPeriod, "접수 종료", "접수종료", "모집 종료", "모집종료", "마감 완료")

        fun containsAny(value: String, vararg candidates: String): Boolean =
            candidates.any(value::contains)

        fun categories(category: String?): List<String> {
            val value = category ?: return emptyList()
            if (value.isBlankLikeJava()) return emptyList()
            return CATEGORY_SEPARATOR.split(value)
                .asSequence()
                .map { it.trimLikeJava() }
                .filter { !it.isBlankLikeJava() }
                .distinct()
                .toList()
                .let { java.util.List.copyOf(it) }
        }

        fun regions(hashtags: String?): List<String> {
            val value = hashtags ?: return emptyList()
            if (value.isBlankLikeJava()) return emptyList()

            val regions = LinkedHashSet<String>()
            for (hashtag in value.split(',')) {
                val normalized = hashtag.trimLikeJava()
                if (normalized == "전남광주") {
                    regions += "광주"
                    regions += "전남"
                    continue
                }
                SOURCE_REGION_ALIASES[normalized]?.let(regions::add)
            }
            if ("전국" in regions || regions.size >= 10) return listOf("전국")
            return java.util.List.copyOf(regions)
        }

        fun plainText(html: String?): String {
            val value = html ?: return ""
            if (value.isBlankLikeJava()) return ""
            val withoutBlocks = HTML_BLOCK.matcher(value).replaceAll(" ")
            val withBreaks = HTML_BREAK.matcher(withoutBlocks).replaceAll(" ")
            val withoutTags = HTML_TAG.matcher(withBreaks).replaceAll(" ")
            return WHITESPACE.matcher(
                HtmlUtils.htmlUnescape(withoutTags).replace('\u00a0', ' '),
            ).replaceAll(" ").trimLikeJava()
        }

        fun officialSourceUrl(value: String?): String? {
            val source = value ?: return null
            if (source.isBlankLikeJava()) return null
            return try {
                val uri = URI(source.trimLikeJava())
                val host = uri.host
                val supportedScheme = uri.scheme.equals("https", ignoreCase = true) ||
                    uri.scheme.equals("http", ignoreCase = true)
                val officialHost = host != null &&
                    (host.equals("bizinfo.go.kr", ignoreCase = true) ||
                        host.lowercase(Locale.ROOT).endsWith(".bizinfo.go.kr"))
                if (supportedScheme && officialHost) uri.toString() else null
            } catch (_: URISyntaxException) {
                null
            }
        }

        fun firstPresent(vararg values: String?): String =
            values.firstOrNull { !it.isNullOrBlankLikeJava() }?.trimLikeJava().orEmpty()

        fun textOrEmpty(value: String?): String = value?.trimLikeJava().orEmpty()

        fun containsSearchTerm(value: String, term: String): Boolean {
            val normalizedTerm = normalize(term)
            if (normalizedTerm.isBlankLikeJava()) return false
            val normalizedValue = normalize(value)
            if (ASCII_SEARCH_TERM.matcher(normalizedTerm).matches()) {
                return SEARCH_TOKEN_SEPARATOR.split(normalizedValue).any(normalizedTerm::equals)
            }
            return normalizedTerm in normalizedValue
        }

        fun normalize(value: String?): String {
            if (value == null) return ""
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
            return WHITESPACE.matcher(normalized).replaceAll(" ").trimLikeJava()
        }
    }
}
