package dev.ward.analysis.domain.format;

/** The value type of a formatting option. */
public enum FormatOptionType {
  /** A whole number, such as an indent width. */
  INTEGER,
  /** {@code true} or {@code false}. */
  BOOLEAN,
  /** Free text, usually restricted by the option's allowed values. */
  TEXT
}
