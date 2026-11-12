package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The rules about security requirements and the schemes they name. */
final class SecurityRules {

  private SecurityRules() {}

  /** {@code oas3-operation-security-defined}: a requirement names a defined security scheme. */
  static List<OpenApiFinding> operationSecurityDefined(final OpenApiDocument document) {
    final Set<String> defined =
        document.parts().component("securitySchemes").stream()
            .map(DocNode.Entry::key)
            .collect(Collectors.toSet());
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      operation.node().get("security").ifPresent(security -> check(security, defined, findings));
    }
    document.root().get("security").ifPresent(security -> check(security, defined, findings));
    return findings;
  }

  private static void check(
      final DocNode security, final Set<String> defined, final List<OpenApiFinding> findings) {
    for (final DocNode requirement : security.items()) {
      for (final DocNode.Entry scheme : requirement.entries()) {
        if (!defined.contains(scheme.key())) {
          findings.add(
              Findings.in(
                  requirement,
                  scheme.keySpan(),
                  "Security definition points a non-existent securityScheme `"
                      + scheme.key()
                      + "`"));
        }
      }
    }
  }
}
