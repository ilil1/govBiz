package ai.govbiz.core.dto.support;

import java.util.List;

import ai.govbiz.core.service.SupportProgramSearchResult;

public record SupportProgramSearchResponse(
        String query,
        List<SupportProgramResponse> programs
) {

    public static SupportProgramSearchResponse from(SupportProgramSearchResult result) {
        return new SupportProgramSearchResponse(
                result.query(),
                result.programs().stream().map(SupportProgramResponse::from).toList());
    }
}
