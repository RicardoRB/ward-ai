package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;

/** The rules about the path keys of {@code paths}. */
final class PathRules {

  private PathRules() {}

  /** {@code path-declarations-must-exist}: a path has no empty {@code {}} declaration. */
  static List<OpenApiFinding> declarationsMustExist(final OpenApiDocument document) {
    return keys(
        document,
        path -> path.contains("{}"),
        "Path parameter declarations must not be empty ex. `/api/{}` is invalid: matches the"
            + " expression `{}`");
  }

  /** {@code path-keys-no-trailing-slash}: a path does not end with a slash. */
  static List<OpenApiFinding> noTrailingSlash(final OpenApiDocument document) {
    return keys(
        document,
        path -> path.length() > 1 && path.endsWith("/"),
        "Path must not end with a slash: matches the expression `.+\\/$`");
  }

  /** {@code path-not-include-query}: a path has no query string. */
  static List<OpenApiFinding> notIncludeQuery(final OpenApiDocument document) {
    return keys(
        document,
        path -> path.contains("?"),
        "Path must not include query string: matches the expression `\\?`");
  }

  /** The segments after the leading slash of a path key. */
  static List<String> segments(final String path) {
    final String[] parts = path.split("/", -1);
    return List.of(parts).subList(Math.min(1, parts.length), parts.length);
  }

  private static List<OpenApiFinding> keys(
      final OpenApiDocument document,
      final java.util.function.Predicate<String> flagged,
      final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      if (flagged.test(path.key())) {
        findings.add(Findings.in(path.value(), path.keySpan(), message));
      }
    }
    return findings;
  }
}
