package ai.govbiz.core.service;

import java.util.List;

import ai.govbiz.core.client.ai.AiSearchIntentAnalysisMode;

public record AnalyzedSearchIntent(
        List<String> keywords,
        List<String> regions,
        List<String> categories,
        List<String> targetTerms,
        boolean clarificationNeeded,
        String clarificationQuestion,
        AiSearchIntentAnalysisMode analysisMode
) {
}
