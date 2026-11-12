package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OperationNodes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** {@code path-params}: the path parameters of a path are declared, required and not repeated. */
final class PathParameterRules {

  private static final List<String> METHODS =
      List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
  private static final String PARAMETERS = "parameters";
  private static final String PATH_IN = "path";

  private PathParameterRules() {}

  /** Reports every way a path and its path parameters disagree. */
  static List<OpenApiFinding> pathParams(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final List<DocNode.Entry> paths = document.parts().paths();
    for (int index = 0; index < paths.size(); index++) {
      final DocNode.Entry path = paths.get(index);
      final DocNode item = document.resolve(path.value());
      findings.addAll(equivalent(document, paths.subList(0, index), path, item));
      findings.addAll(template(document, path, item));
      findings.addAll(declared(document, path, item));
    }
    return findings;
  }

  private static List<OpenApiFinding> equivalent(
      final OpenApiDocument document,
      final List<DocNode.Entry> earlier,
      final DocNode.Entry path,
      final DocNode item) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final String method : methods(item)) {
      for (final DocNode.Entry other : earlier) {
        if (methods(document.resolve(other.value())).contains(method)
            && PathTemplates.normalized(other.key()).equals(PathTemplates.normalized(path.key()))) {
          findings.add(
              Findings.in(
                  path.value(),
                  path.keySpan(),
                  method.toUpperCase(Locale.ROOT)
                      + ": paths `"
                      + other.key()
                      + "` and `"
                      + path.key()
                      + "` must not be equivalent, paths must be unique for the same HTTP method"));
          break;
        }
      }
    }
    return findings;
  }

  /** The findings about the parameters named in the path template itself. */
  private static List<OpenApiFinding> template(
      final OpenApiDocument document, final DocNode.Entry path, final DocNode item) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    for (final String name : PathTemplates.names(path.key())) {
      if (!seen.add(name)) {
        findings.add(
            Findings.in(
                path.value(),
                path.keySpan(),
                "path `"
                    + path.key()
                    + "` must not use the parameter `"
                    + name
                    + "` multiple times"));
      }
      final boolean atPathItem = pathParameter(document, item, name);
      for (final DocNode.Entry method : item.entries()) {
        if (METHODS.contains(method.key())
            && !atPathItem
            && !pathParameter(document, document.resolve(method.value()), name)) {
          findings.add(
              Findings.in(
                  path.value(),
                  path.keySpan(),
                  "parameter named `"
                      + name
                      + "` must be defined as part of the path `"
                      + path.key()
                      + "` definition, or in the `"
                      + method.key().toUpperCase(Locale.ROOT)
                      + "` operation(s)"));
        }
      }
    }
    return findings;
  }

  /** The findings about the path parameters that the path item and its operations declare. */
  private static List<OpenApiFinding> declared(
      final OpenApiDocument document, final DocNode.Entry path, final DocNode item) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Set<String> names = new LinkedHashSet<>(PathTemplates.names(path.key()));
    declaredBy(document, item, new Scope(path.key(), "path parameter", names), findings);
    for (final DocNode.Entry method : item.entries()) {
      if (METHODS.contains(method.key())) {
        declaredBy(
            document,
            document.resolve(method.value()),
            scopeOf(path.key(), method.key(), names),
            findings);
      }
    }
    return findings;
  }

  private static void declaredBy(
      final OpenApiDocument document,
      final DocNode owner,
      final Scope scope,
      final List<OpenApiFinding> findings) {
    final Span at =
        owner.entries().stream()
            .filter(entry -> PARAMETERS.equals(entry.key()))
            .findFirst()
            .map(DocNode.Entry::keySpan)
            .orElse(owner.span());
    final Set<String> duplicates = new HashSet<>();
    for (final DocNode raw : OperationNodes.parameters(owner)) {
      final DocNode parameter = document.resolve(raw);
      if (PATH_IN.equals(parameter.string("in").orElse(""))) {
        final String name = parameter.string("name").orElse("");
        final boolean required =
            parameter.get("required").map(node -> "false".equals(node.text())).orElse(false);
        final List<String> problems = problemsOf(scope, name, required, duplicates);
        problems.forEach(message -> findings.add(Findings.in(owner, at, message)));
      }
    }
  }

  private static Scope scopeOf(final String path, final String method, final Set<String> names) {
    return new Scope(path, "`" + method.toUpperCase(Locale.ROOT) + "` parameter", names);
  }

  private static List<String> problemsOf(
      final Scope scope,
      final String name,
      final boolean notRequired,
      final Set<String> duplicates) {
    final List<String> problems = new ArrayList<>();
    if (notRequired) {
      problems.add(requiredMessage(scope, name));
    }
    if (!duplicates.add(name)) {
      problems.add(
          scope.label()
              + " named `"
              + name
              + "` at `"
              + scope.path()
              + "` is a duplicate of another parameter with the same name");
    }
    if (!scope.names().contains(name)) {
      problems.add(
          scope.label() + " named `" + name + "` does not exist in path `" + scope.path() + "`");
    }
    return problems;
  }

  private static String requiredMessage(final Scope scope, final String name) {
    return scope.label().startsWith("`")
        ? "`"
            + scope.path()
            + "` "
            + scope.label()
            + " named `"
            + name
            + "` must have `required` set to `true`"
        : scope.label()
            + " named `"
            + name
            + "` at `"
            + scope.path()
            + "` must have `required` set to `true`";
  }

  /** The path, the way a message names the parameters, and the names its template declares. */
  private record Scope(String path, String label, Set<String> names) {}

  private static boolean pathParameter(
      final OpenApiDocument document, final DocNode owner, final String name) {
    return OperationNodes.parameters(owner).stream()
        .map(document::resolve)
        .anyMatch(
            parameter ->
                PATH_IN.equals(parameter.string("in").orElse(""))
                    && name.equals(parameter.string("name").orElse("")));
  }

  private static List<String> methods(final DocNode item) {
    return item.entries().stream().map(DocNode.Entry::key).filter(METHODS::contains).toList();
  }
}
