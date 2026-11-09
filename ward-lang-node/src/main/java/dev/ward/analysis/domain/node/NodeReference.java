package dev.ward.analysis.domain.node;

import java.util.Optional;
import java.util.Set;

/**
 * A module reference with the kind the summary recorded: {@code static}, {@code type}, {@code
 * require} or {@code dynamic}. The kind selects the export conditions that apply.
 *
 * @param kind the kind prefix without its colon
 * @param specifier the module specifier as written
 */
record NodeReference(String kind, String specifier) {
  private static final String STATIC = "static";
  private static final String TYPE = "type";
  private static final String REQUIRE = "require";
  private static final String DYNAMIC = "dynamic";
  private static final Set<String> KINDS = Set.of(STATIC, TYPE, REQUIRE, DYNAMIC);

  /** Splits a summary's reference into its kind and specifier. */
  static Optional<NodeReference> parse(final String recorded) {
    final int colon = recorded.indexOf(':');
    if (colon < 0 || !KINDS.contains(recorded.substring(0, colon))) {
      return Optional.empty();
    }
    return Optional.of(
        new NodeReference(recorded.substring(0, colon), recorded.substring(colon + 1)));
  }

  /**
   * Returns whether the reference is a CommonJS {@code require}, which uses the require condition.
   */
  boolean isRequire() {
    return REQUIRE.equals(kind);
  }
}
