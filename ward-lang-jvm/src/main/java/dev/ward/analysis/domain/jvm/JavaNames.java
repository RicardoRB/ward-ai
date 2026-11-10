package dev.ward.analysis.domain.jvm;

import java.util.List;

/** The qualified and simple names of Java types, as the specifiers of summaries spell them. */
final class JavaNames {
  private static final char SEPARATOR = '.';

  private JavaNames() {}

  /** Joins the first {@code count} segments of a qualified name. */
  static String prefix(final String[] segments, final int count) {
    return String.join(String.valueOf(SEPARATOR), List.of(segments).subList(0, count));
  }

  /** Qualifies {@code name} by {@code owner}, which is empty for the default package. */
  static String qualify(final String owner, final String name) {
    return owner.isEmpty() ? name : owner + SEPARATOR + name;
  }

  /** Returns the last segment of a qualified name. */
  static String simpleName(final String qualified) {
    return qualified.substring(qualified.lastIndexOf(SEPARATOR) + 1);
  }

  /** Returns the part of a specifier after its kind and colon. */
  static String valueOf(final String specifier) {
    return specifier.substring(specifier.indexOf(':') + 1);
  }
}
