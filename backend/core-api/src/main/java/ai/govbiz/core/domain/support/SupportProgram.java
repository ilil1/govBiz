package ai.govbiz.core.domain.support;

import java.time.LocalDate;
import java.util.List;

public record SupportProgram(
        String id,
        String title,
        String organization,
        String summary,
        List<String> categories,
        List<String> regions,
        String targetDescription,
        String supportAmount,
        String applicationPeriod,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        SupportProgramStatus status,
        String sourceName,
        String sourceUrl,
        List<String> matchedReasons
) {
}
