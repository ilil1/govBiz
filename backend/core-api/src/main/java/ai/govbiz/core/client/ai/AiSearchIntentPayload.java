package ai.govbiz.core.client.ai;

import java.util.List;

/**
 * AI Service의 내부 검색 의도 계약이다. Boolean wrapper는 누락된 필드와 false를 구분하기 위해
 * 의도적으로 사용한다.
 */
public record AiSearchIntentPayload(
        String originalQuery,
        List<String> keywords,
        List<String> regions,
        List<String> categories,
        List<String> targetTerms,
        Boolean acceptingOnly,
        Boolean clarificationNeeded,
        String clarificationQuestion,
        AiSearchIntentAnalysisMode analysisMode
) {
}
