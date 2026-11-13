package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rule that ties every security requirement to a security scheme. */
final class SecurityRules {

  private static final String LOCATION = "\"security\" values must match a scheme defined in the ";

  private SecurityRules() {}

  /** {@code oas3-operation-security-defined}: a requirement names a scheme and its scopes. */
  static List<OpenApiFinding> operationSecurityDefined(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode requirement : document.parts().security()) {
      findings.addAll(requirement(document, document.resolve(requirement), "API"));
    }
    for (final Operation operation : document.parts().operations()) {
      document
          .get(operation.node(), "security", View.RESOLVED)
          .filter(security -> security.kind() == DocNode.Kind.ARRAY)
          .ifPresent(
              security ->
                  security
                      .items()
                      .forEach(
                          item ->
                              findings.addAll(
                                  requirement(document, document.resolve(item), "Operation"))));
    }
    return findings;
  }

  private static List<OpenApiFinding> requirement(
      final OpenApiDocument document, final DocNode requirement, final String owner) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    if (!valid(requirement)) {
      return findings;
    }
    final Optional<DocNode> schemes =
        Fields.at(document, document.root(), "components", "securitySchemes")
            .filter(node -> node.kind() == DocNode.Kind.OBJECT);
    for (final DocNode.Entry entry : requirement.entries()) {
      final Optional<DocNode> scheme = schemes.flatMap(node -> node.get(entry.key()));
      if (scheme.isEmpty()) {
        findings.add(
            Findings.atValue(
                entry.value(), owner + " " + LOCATION + "\"components.securitySchemes\" object."));
        continue;
      }
      for (final DocNode scope : entry.value().items()) {
        if (!scopeDefined(document, scope.text(), scheme.get())) {
          findings.add(
              Findings.atValue(scope, "\"" + scope.text() + "\" must be listed among scopes."));
        }
      }
    }
    return findings;
  }

  /** Spectral reads only objects whose values are arrays of strings, and not the empty object. */
  private static boolean valid(final DocNode requirement) {
    return requirement.kind() == DocNode.Kind.OBJECT
        && !requirement.entries().isEmpty()
        && requirement.entries().stream()
            .allMatch(
                entry ->
                    entry.value().kind() == DocNode.Kind.ARRAY
                        && entry.value().items().stream()
                            .allMatch(item -> item.kind() == DocNode.Kind.STRING));
  }

  private static boolean scopeDefined(
      final OpenApiDocument document, final String scope, final DocNode scheme) {
    final Optional<DocNode> flows = document.get(scheme, "flows", View.RESOLVED);
    if (flows.isEmpty() || flows.get().kind() != DocNode.Kind.OBJECT) {
      return false;
    }
    return flows.get().children().stream()
        .map(document::resolve)
        .anyMatch(
            flow ->
                document
                    .get(flow, "scopes", View.RESOLVED)
                    .filter(scopes -> scopes.kind() == DocNode.Kind.OBJECT)
                    .filter(scopes -> scopes.get(scope).isPresent())
                    .isPresent());
  }
}
