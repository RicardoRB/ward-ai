package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;
import java.util.Optional;

/**
 * The type of a response header, which oasdiff judges by type and format together: it breaks a
 * client when the base type was not text and the new one is not a narrower or equal form, or when a
 * format was widened or replaced.
 */
final class HeaderTypeChanges {

  private static final String TYPE = "type";
  private static final String FORMAT = "format";
  private static final List<String> NARROWING_FORMATS = List.of("int64", "int32");

  private HeaderTypeChanges() {}

  /** The header type change of one schema pair, as the kind {@code header-type-changed}. */
  static List<SchemaChange> changes(
      final SchemaSides sides, final DocNode base, final DocNode revision) {
    if (sides.request() || !changed(base, revision)) {
      return List.of();
    }
    return List.of(new SchemaChange("header-type-changed", revision.get(TYPE).orElse(revision)));
  }

  private static boolean changed(final DocNode base, final DocNode revision) {
    final Optional<String> before = base.string(TYPE);
    final Optional<String> after = revision.string(TYPE);
    if (before.isEmpty() || after.isEmpty()) {
      return false;
    }
    return before.equals(after)
        ? formatChanged(base.string(FORMAT), revision.string(FORMAT))
        : typeReplaced(before.get(), after.get());
  }

  /** A format that was widened or swapped; adding a format or narrowing int64 to int32 is fine. */
  private static boolean formatChanged(
      final Optional<String> before, final Optional<String> after) {
    if (before.isEmpty() || before.equals(after)) {
      return false;
    }
    final boolean narrowed =
        after.isPresent()
            && NARROWING_FORMATS.contains(before.get())
            && NARROWING_FORMATS.indexOf(after.get()) > NARROWING_FORMATS.indexOf(before.get());
    return !narrowed;
  }

  /** A type replaced by one that is neither text-compatible, narrower nor the generalized one. */
  private static boolean typeReplaced(final String before, final String after) {
    final boolean narrowed = "number".equals(before) && "integer".equals(after);
    final boolean generalized = "integer".equals(before) && "number".equals(after);
    return !"string".equals(before) && !narrowed && !generalized;
  }
}
