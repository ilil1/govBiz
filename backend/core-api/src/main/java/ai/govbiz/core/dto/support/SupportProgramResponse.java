package ai.govbiz.core.dto.support;

import java.util.List;

import ai.govbiz.core.domain.support.SupportProgram;
import ai.govbiz.core.domain.support.SupportProgramStatus;

public record SupportProgramResponse(
        String id,
        String title,
        String organization,
        String summary,
        List<String> categories,
        List<String> regions,
        String targetDescription,
        String supportAmount,
        String applicationPeriod,
        String applicationStartDate,
        String applicationEndDate,
        SupportProgramStatus status,
        String sourceName,
        String sourceUrl,
        List<String> matchedReasons
) {

    public static SupportProgramResponse from(SupportProgram program) {
        return new SupportProgramResponse(
                program.id(),
                program.title(),
                program.organization(),
                program.summary(),
                program.categories(),
                program.regions(),
                program.targetDescription(),
                program.supportAmount(),
                program.applicationPeriod(),
                program.applicationStartDate() == null
                        ? null
                        : program.applicationStartDate().toString(),
                program.applicationEndDate() == null
                        ? null
                        : program.applicationEndDate().toString(),
                program.status(),
                program.sourceName(),
                program.sourceUrl(),
                program.matchedReasons());
    }
}
