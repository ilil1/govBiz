package ai.govbiz.core.text

internal fun String.trimLikeJava(): String = trim { it <= ' ' }

internal fun String.isBlankLikeJava(): Boolean =
    codePoints().allMatch(Character::isWhitespace)

internal fun String?.isNullOrBlankLikeJava(): Boolean =
    this == null || isBlankLikeJava()
