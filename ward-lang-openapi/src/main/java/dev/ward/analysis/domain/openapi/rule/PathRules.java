package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** The rules about the path keys of {@code paths}. */
final class PathRules {

  private static final Pattern KEBAB = Pattern.compile("^[{}a-z\\d-.]+$");
  private static final Set<String> VERBS =
      Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

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

  /** {@code paths-kebab-case}: the static segments of a path are kebab-case. */
  static List<OpenApiFinding> kebabCase(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      final List<String> bad = notKebab(path.key());
      if (!bad.isEmpty()) {
        findings.add(
            Findings.in(
                path.value(),
                path.keySpan(),
                "path segments `" + String.join("`, `", bad) + "` do not use kebab-case"));
      }
    }
    return findings;
  }

  private static List<String> notKebab(final String path) {
    return segments(path).stream()
        .filter(segment -> !segment.isEmpty() && !KEBAB.matcher(segment).matches())
        .filter(segment -> !isTemplate(segment))
        .toList();
  }

  /** {@code no-http-verbs-in-path}: no segment of a path is an HTTP verb. */
  static List<OpenApiFinding> noVerbs(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      segments(path.key()).stream()
          .filter(segment -> VERBS.contains(segment.toLowerCase(Locale.ROOT)))
          .findFirst()
          .ifPresent(
              verb ->
                  findings.add(
                      Findings.atValue(
                          path.value(),
                          "path `" + path.key() + "` contains an HTTP Verb `" + verb + "`")));
    }
    return findings;
  }

  /** The segments after the leading slash of a path key. */
  static List<String> segments(final String path) {
    final String[] parts = path.split("/", -1);
    return List.of(parts).subList(Math.min(1, parts.length), parts.length);
  }

  private static boolean isTemplate(final String segment) {
    final int open = segment.indexOf('{');
    return open >= 0 && segment.indexOf('}', open + 1) >= 0;
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
