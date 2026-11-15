package dev.sevendigits

/** Engine that supplied a character; scores from different engines are not comparable. */
public enum class RecognitionSource {
    /** The app-supplied LiteRT detector, including its small-character passes. */
    SEVEN_SEGMENT,
    /** Bundled ML Kit Latin text recognition, which can also recognize ordinary digits. */
    GENERAL_TEXT,
}
