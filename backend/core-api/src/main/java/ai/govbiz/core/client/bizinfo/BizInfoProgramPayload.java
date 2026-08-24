package ai.govbiz.core.client.bizinfo;

public record BizInfoProgramPayload(
        String title,
        String sourceUrl,
        String id,
        String jurisdictionOrganization,
        String executingOrganization,
        String summaryHtml,
        String category,
        String createdAt,
        String applicationPeriod,
        String updatedAt,
        String target,
        String hashtags,
        String applicationMethod,
        String applicationUrl
) {
}
