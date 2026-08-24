package ai.govbiz.core.service;

import java.util.List;

import ai.govbiz.core.domain.support.SupportProgram;

public record SupportProgramSearchResult(String query, List<SupportProgram> programs) {
}
