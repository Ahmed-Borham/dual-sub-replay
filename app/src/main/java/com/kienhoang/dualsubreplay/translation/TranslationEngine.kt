package com.kienhoang.dualsubreplay.translation

internal const val TRANSLATION_ENGINE_PREFERENCE = "translation_engine"

/** Which engine translates subtitles. Google online is the default. */
enum class TranslationEngine(
    val storageValue: String,
) {
    ON_DEVICE("on_device"),
    GOOGLE_WEB("google_web"),
}

internal val DEFAULT_TRANSLATION_ENGINE = TranslationEngine.GOOGLE_WEB

/** The saved engine, or Google when nothing valid is saved. */
internal fun storedTranslationEngine(raw: String?): TranslationEngine =
    TranslationEngine.entries.firstOrNull { it.storageValue == raw } ?: DEFAULT_TRANSLATION_ENGINE
