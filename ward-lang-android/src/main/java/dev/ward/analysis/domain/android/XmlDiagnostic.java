package dev.ward.analysis.domain.android;

/**
 * A syntax problem of an XML text, with its 1-based position.
 *
 * @param message what is wrong
 * @param line the line of the problem
 * @param column the column of the problem
 */
public record XmlDiagnostic(String message, int line, int column) {}
