package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The OWASP rules about the security requirements of operations. */
final class OwaspSecurityRules {

  private static final String SECURITY = "security";
  private static final String ADMIN = "/admin";

  private OwaspSecurityRules() {}

  /** {@code owasp-api2-2023-write-restricted}: a write operation has a security requirement. */
  static List<OpenApiFinding> writeRestricted(final OpenApiDocument document) {
    return restricted(
        document,
        List.of("post", "put", "patch", "delete"),
        "This write operation is not protected by any security scheme.");
  }

  /** {@code owasp-api2-2023-read-restricted}: a read operation has a security requirement. */
  static List<OpenApiFinding> readRestricted(final OpenApiDocument document) {
    return restricted(
        document,
        List.of("get", "head"),
        "This read operation is not protected by any security scheme.");
  }

  private static List<OpenApiFinding> restricted(
      final OpenApiDocument document, final List<String> methods, final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Optional<DocNode> global = document.get(document.root(), SECURITY, View.RESOLVED);
    for (final Operation operation : document.parts().operations()) {
      if (!methods.contains(operation.method())) {
        continue;
      }
      final Optional<DocNode> own = document.get(operation.node(), SECURITY, View.RESOLVED);
      final Optional<DocNode> effective = own.isPresent() ? own : global;
      if (effective.isEmpty() || !defined(effective.get())) {
        findings.add(Findings.atValue(own.orElse(operation.node()), message));
      }
    }
    return findings;
  }

  /** JavaScript: a security value is missing when it is falsy or an empty array. */
  private static boolean defined(final DocNode security) {
    return security.kind() == DocNode.Kind.ARRAY
        ? !security.items().isEmpty()
        : Fields.truthy(security);
  }

  /** {@code owasp-api5-2023-admin-security-unique}: an admin path has its own security. */
  static List<OpenApiFinding> adminSecurityUnique(final OpenApiDocument document) {
    final List<String> ordinary = new ArrayList<>();
    final List<DocNode> admin = new ArrayList<>();
    final List<String> adminPaths = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      for (final DocNode entry : requirements(document, operation)) {
        if (operation.path().contains(ADMIN)) {
          admin.add(entry);
          adminPaths.add(operation.path());
        } else {
          ordinary.add(JsonText.write(entry));
        }
      }
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (int index = 0; index < admin.size(); index++) {
      if (ordinary.contains(JsonText.write(admin.get(index)))) {
        findings.add(
            Findings.atValue(
                admin.get(index),
                "Admin endpoint "
                    + adminPaths.get(index)
                    + " has the same security requirement as a non-admin endpoint."));
      }
    }
    return findings;
  }

  /** The object or array requirements of the operation's own {@code security}. */
  private static List<DocNode> requirements(
      final OpenApiDocument document, final Operation operation) {
    return document
        .get(operation.node(), SECURITY, View.RESOLVED)
        .filter(security -> security.kind() == DocNode.Kind.ARRAY)
        .map(
            security ->
                security.items().stream()
                    .filter(
                        entry ->
                            entry.kind() == DocNode.Kind.OBJECT
                                || entry.kind() == DocNode.Kind.ARRAY)
                    .toList())
        .orElse(List.of());
  }
}
