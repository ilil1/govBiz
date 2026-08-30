package ai.govbiz.core.sampleitem.domain

/** 프런트엔드부터 Core API까지 흐르는 최소 예제 도메인입니다. */
class SampleItem(
    name: String?,
    val category: SampleCategory?,
    note: String?,
) {
    val name: String = normalizeRequired(name, "name")
    val note: String? = normalizeOptional(note)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is SampleItem &&
            name == other.name &&
            category == other.category &&
            note == other.note

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + (note?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "SampleItem[name=$name, category=$category, note=$note]"

    private companion object {
        fun normalizeRequired(value: String?, field: String): String {
            val normalized = (value ?: throw NullPointerException("$field must not be null"))
                .trim { Character.isWhitespace(it) }
            if (normalized.isEmpty()) {
                throw IllegalArgumentException("$field must not be blank")
            }
            return normalized
        }

        fun normalizeOptional(value: String?): String? {
            val normalized = value?.trim { Character.isWhitespace(it) } ?: return null
            return normalized.ifEmpty { null }
        }
    }
}
