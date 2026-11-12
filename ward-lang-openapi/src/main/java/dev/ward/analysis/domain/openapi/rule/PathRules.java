package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The rules about the keys of {@code paths}. */
final class PathRules {

  private static final Pattern EMPTY_PARAMETER = Pattern.compile("\\{}");
  private static final Pattern TRAILING_SLASH = Pattern.compile(".+/\\z");
  private static final Pattern QUERY = Pattern.compile("\\?");

  private PathRules() {}

  /** {@code path-declarations-must-exist}: no path has an empty {@code {}} parameter. */
  static List<OpenApiFinding> declarationsMustExist(final OpenApiDocument document) {
    return keys(
        document,
        EMPTY_PARAMETER,
        "Path parameter declarations must not be empty, ex.\"/given/{}\" is invalid.");
  }

  /** {@code path-keys-no-trailing-slash}: no path ends with a slash. */
  static List<OpenApiFinding> noTrailingSlash(final OpenApiDocument document) {
    return keys(document, TRAILING_SLASH, "Path must not end with slash.");
  }

  /** {@code path-not-include-query}: no path has a query string. */
  static List<OpenApiFinding> noQuery(final OpenApiDocument document) {
    return keys(document, QUERY, "Path must not include query string.");
  }

  private static List<OpenApiFinding> keys(
      final OpenApiDocument document, final Pattern pattern, final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      if (pattern.matcher(path.key()).find()) {
        findings.add(Findings.atValue(document.resolve(path.value()), message));
      }
    }
    return findings;
  }
}
