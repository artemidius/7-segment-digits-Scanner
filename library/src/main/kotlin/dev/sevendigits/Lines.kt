package dev.sevendigits

/**
 * Recognized text organized as rows, top to bottom, with fields left to right in each row.
 * Each string is one detected field, not a single digit. Leading zeros, signs, and
 * punctuation are preserved. For example: `listOf(listOf("120"), listOf("80", "65"))`.
 *
 * This is simply a nested list, with no wrapper or visual metadata. Recognition returns an
 * immutable snapshot of both list levels. No detections produces an empty outer list;
 * empty rows or placeholder strings are not inserted for missed content or vertical gaps.
 */
public typealias Lines = List<List<String>>
