package ai.govbiz.core.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ai.govbiz.core.client.ai.AiSearchIntentPayload;
import ai.govbiz.core.client.ai.AiServiceClient;
import ai.govbiz.core.client.ai.AiServiceClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** AI Service의 비신뢰 응답을 검증하고, 실패를 검색 가능한 로컬 폴백으로 바꾼다. */
@Service
public class AiSearchIntentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiSearchIntentService.class);
    private static final int MAX_TERMS_PER_FIELD = 8;
    private static final int MAX_TERM_LENGTH = 60;
    private static final int MAX_CLARIFICATION_LENGTH = 200;
    private static final Set<String> ALLOWED_REGIONS = Set.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주", "전국");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "AI", "창업", "기술", "수출", "경영", "금융", "인력", "내수",
            "제조", "콘텐츠", "소상공인");

    private final AiServiceClient client;

    public AiSearchIntentService(AiServiceClient client) {
        this.client = client;
    }

    public Optional<AnalyzedSearchIntent> analyze(String query, boolean acceptingOnly) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return Optional.empty();
        }

        final AiSearchIntentPayload payload;
        try {
            payload = client.analyzeSearchIntent(normalizedQuery, acceptingOnly);
        } catch (AiServiceClientException exception) {
            LOGGER.debug(
                    "AI search intent is unavailable; using local rules ({})",
                    exception.failure());
            return Optional.empty();
        }

        Optional<AnalyzedSearchIntent> validated = validate(
                payload,
                normalizedQuery,
                acceptingOnly);
        if (validated.isEmpty()) {
            LOGGER.debug("AI search intent violated the internal contract; using local rules");
        }
        return validated;
    }

    private static Optional<AnalyzedSearchIntent> validate(
            AiSearchIntentPayload payload,
            String expectedQuery,
            boolean expectedAcceptingOnly
    ) {
        if (payload == null
                || !expectedQuery.equals(payload.originalQuery())
                || payload.acceptingOnly() == null
                || payload.acceptingOnly() != expectedAcceptingOnly
                || payload.clarificationNeeded() == null
                || payload.analysisMode() == null) {
            return Optional.empty();
        }

        Optional<List<String>> keywords = terms(payload.keywords(), null);
        Optional<List<String>> regions = terms(payload.regions(), ALLOWED_REGIONS);
        Optional<List<String>> categories = terms(payload.categories(), ALLOWED_CATEGORIES);
        Optional<List<String>> targetTerms = terms(payload.targetTerms(), null);
        if (keywords.isEmpty() || regions.isEmpty() || categories.isEmpty() || targetTerms.isEmpty()) {
            return Optional.empty();
        }

        String clarificationQuestion = trimToNull(payload.clarificationQuestion());
        if (Boolean.TRUE.equals(payload.clarificationNeeded())) {
            if (clarificationQuestion == null
                    || clarificationQuestion.length() > MAX_CLARIFICATION_LENGTH) {
                return Optional.empty();
            }
        } else if (clarificationQuestion != null) {
            return Optional.empty();
        }

        return Optional.of(new AnalyzedSearchIntent(
                keywords.get(),
                regions.get(),
                categories.get(),
                targetTerms.get(),
                payload.clarificationNeeded(),
                clarificationQuestion,
                payload.analysisMode()));
    }

    private static Optional<List<String>> terms(List<String> values, Set<String> allowed) {
        if (values == null || values.size() > MAX_TERMS_PER_FIELD) {
            return Optional.empty();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String term = trimToNull(value);
            if (term == null
                    || term.length() > MAX_TERM_LENGTH
                    || containsControlCharacter(term)
                    || (allowed != null && !allowed.contains(term))) {
                return Optional.empty();
            }
            normalized.add(term);
        }
        return Optional.of(List.copyOf(new ArrayList<>(normalized)));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
