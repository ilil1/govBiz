package io.basearchitecture.core.domain.sample;

import java.util.Objects;

/** 프런트엔드부터 Core API까지 흐르는 최소 예제 도메인입니다. */
public record SampleItem(
        String name,
        SampleCategory category,
        String note
) {

    public SampleItem {
        name = normalizeRequired(name, "name");
        note = normalizeOptional(note);
    }

    private static String normalizeRequired(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
