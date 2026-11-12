package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The OWASP rules that ask every operation to define its {@code security}. */
final class OwaspSecurityRules {

  private static final String SECURITY = "security";
  private static final Set<String> UNSAFE = Set.of("post", "put", "patch", "delete");
  private static final Set<String> SAFE = Set.of("get", "head");

  private OwaspSecurityRules() {}

  /** {@code owasp-protection-global-unsafe}: unsafe methods define security, which may be null. */
  static List<OpenApiFinding> unsafe(final OpenApiDocument document) {
    return check(document, UNSAFE, true);
  }

  /** {@code owasp-protection-global-unsafe-strict}: unsafe methods define non-null security. */
  static List<OpenApiFinding> unsafeStrict(final OpenApiDocument document) {
    return check(document, UNSAFE, false);
  }

  /** {@code owasp-protection-global-safe}: safe methods define security, which may be null. */
  static List<OpenApiFinding> safe(final OpenApiDocument document) {
    return check(document, SAFE, true);
  }

  private static List<OpenApiFinding> check(
      final OpenApiDocument document, final Set<String> methods, final boolean nullable) {
    final Optional<DocNode> global = document.root().get(SECURITY);
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      if (methods.contains(operation.method())) {
        findings.addAll(operation(operation, global, nullable));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> operation(
      final Operation operation, final Optional<DocNode> global, final boolean nullable) {
    final Optional<DocNode> own = operation.node().get(SECURITY);
    final String where =
        "for path `" + operation.path() + "` in method `" + operation.method() + "`";
    final List<OpenApiFinding> findings = new ArrayList<>();
    if (own.isEmpty() && global.isEmpty()) {
      findings.add(Findings.at(operation.node(), "`security` was not defined " + where));
      return findings;
    }
    if (own.isPresent() && own.get().items().isEmpty()) {
      findings.add(Findings.at(operation.node(), "`security` is empty " + where));
    }
    if (!nullable) {
      findings.addAll(nullElements(own.isPresent() ? own : global, where));
    }
    return findings;
  }

  private static List<OpenApiFinding> nullElements(
      final Optional<DocNode> security, final String where) {
    return security.stream()
        .flatMap(node -> node.items().stream())
        .filter(requirement -> requirement.entries().isEmpty())
        .map(requirement -> Findings.atValue(requirement, "`security` has null elements " + where))
        .toList();
  }
}
