package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code path-params}: the parameters of a path template are defined, valid and used. */
final class PathParameterRules {

  private static final Pattern TEMPLATE = Pattern.compile("(\\{;?\\??[a-zA-Z0-9_-]+\\*?\\})");
  private static final Pattern DECORATION = Pattern.compile("[{}?*;]");
  private static final List<String> METHODS =
      List.of("get", "head", "post", "put", "patch", "delete", "options", "trace");

  private PathParameterRules() {}

  /** Checks every path and every operation of the document. */
  static List<OpenApiFinding> pathParams(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Map<String, String> unique = new HashMap<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      final DocNode item = document.resolve(path.value());
      if (item.kind() != DocNode.Kind.OBJECT) {
        continue;
      }
      final String normalized = TEMPLATE.matcher(path.key()).replaceAll("%");
      final String first = unique.putIfAbsent(normalized, path.key());
      if (first != null) {
        findings.add(
            Findings.atValue(
                item,
                "Paths \"" + first + "\" and \"" + path.key() + "\" must not be equivalent."));
      }
      inspect(document, path.key(), item, findings);
    }
    return findings;
  }

  private static void inspect(
      final OpenApiDocument document,
      final String path,
      final DocNode item,
      final List<OpenApiFinding> findings) {
    final List<String> expected = expected(path, item, findings);
    final Map<String, DocNode> top = new LinkedHashMap<>();
    parameters(document, item, top, findings);
    final Template template = new Template(path, expected, top);
    for (final DocNode.Entry method : item.entries()) {
      final DocNode operation = document.resolve(method.value());
      if (METHODS.contains(method.key()) && operation.kind() == DocNode.Kind.OBJECT) {
        operation(document, template, operation, findings);
      }
    }
  }

  private static List<String> expected(
      final String path, final DocNode item, final List<OpenApiFinding> findings) {
    final List<String> names = new ArrayList<>();
    final Matcher matcher = TEMPLATE.matcher(path);
    while (matcher.find()) {
      final String name = DECORATION.matcher(matcher.group()).replaceAll("");
      if (names.contains(name)) {
        findings.add(
            Findings.atValue(
                item,
                "Path \"" + path + "\" must not use parameter \"{" + name + "}\" multiple times."));
      } else {
        names.add(name);
      }
    }
    return names;
  }

  /** A path, the parameter names its template expects and the path item's own parameters. */
  private record Template(String path, List<String> expected, Map<String, DocNode> top) {}

  private static void operation(
      final OpenApiDocument document,
      final Template template,
      final DocNode operation,
      final List<OpenApiFinding> findings) {
    final String path = template.path();
    final List<String> expected = template.expected();
    final Map<String, DocNode> top = template.top();
    final Map<String, DocNode> own = new LinkedHashMap<>();
    parameters(document, operation, own, findings);
    final Map<String, DocNode> defined = new LinkedHashMap<>(top);
    defined.putAll(own);
    for (final Map.Entry<String, DocNode> parameter : defined.entrySet()) {
      if (!expected.contains(parameter.getKey())) {
        findings.add(
            Findings.atValue(
                parameter.getValue(),
                "Parameter \"" + parameter.getKey() + "\" must be used in path \"" + path + "\"."));
      }
    }
    for (final String name : expected) {
      if (!defined.containsKey(name)) {
        findings.add(
            Findings.atValue(
                operation,
                "Operation must define parameter \"{"
                    + name
                    + "}\" as expected by path \""
                    + path
                    + "\"."));
      }
    }
  }

  /** Records the named path parameters of {@code owner} and reports the invalid ones. */
  private static void parameters(
      final OpenApiDocument document,
      final DocNode owner,
      final Map<String, DocNode> seen,
      final List<OpenApiFinding> findings) {
    final Optional<DocNode> list = document.get(owner, "parameters", View.RESOLVED);
    if (list.isEmpty() || list.get().kind() != DocNode.Kind.ARRAY) {
      return;
    }
    for (final DocNode item : list.get().items()) {
      final DocNode parameter = document.resolve(item);
      final Optional<DocNode> name = parameter.get("name");
      if (parameter.kind() != DocNode.Kind.OBJECT
          || name.isEmpty()
          || !parameter.get("in").map(in -> "path".equals(in.text())).orElse(false)) {
        continue;
      }
      if (!parameter
          .get("required")
          .map(required -> "true".equals(required.text()))
          .orElse(false)) {
        findings.add(
            Findings.atValue(
                parameter,
                "Path parameter \""
                    + name.get().text()
                    + "\" must have \"required\" property that is set to \"true\"."));
      }
      if (seen.containsKey(name.get().text())) {
        findings.add(
            Findings.atValue(
                parameter,
                "Path parameter \""
                    + name.get().text()
                    + "\" must not be defined multiple times."));
      } else {
        seen.put(name.get().text(), parameter);
      }
    }
  }
}
