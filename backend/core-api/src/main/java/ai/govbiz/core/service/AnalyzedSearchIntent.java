package ai.govbiz.core.service;

import java.util.List;

public record AnalyzedSearchIntent(
        List<String> keywords,
        List<String> regions,
        List<String> categories,
        List<String> targetTerms,
        boolean clarificationNeeded,
        String clarificationQuestion
) {
}
