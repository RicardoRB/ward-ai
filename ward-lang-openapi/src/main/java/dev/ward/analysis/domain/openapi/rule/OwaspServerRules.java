package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The OWASP rules about the {@code servers} of a document. */
final class OwaspServerRules {

  private static final Pattern HTTP = Pattern.compile("^http:");
  private static final Pattern ENVIRONMENT =
      Pattern.compile(
          "(local|sandbox|alpha|beta|test|testing|stag|staging|prod|production|next|preprod|preproduction)",
          Pattern.CASE_INSENSITIVE);

  private OwaspServerRules() {}

  /** {@code owasp-api8-2023-no-server-http}: no server URL starts with {@code http:}. */
  static List<OpenApiFinding> noServerHttp(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    document
        .get(document.root(), "servers", View.RESOLVED)
        .ifPresent(
            servers -> {
              for (final Nodes.Located located : Nodes.resolved(document, servers)) {
                if ("url".equals(located.key())
                    && located.node().kind() == DocNode.Kind.STRING
                    && HTTP.matcher(located.node().text()).find()) {
                  findings.add(
                      Findings.atValue(
                          located.node(),
                          "Server URLs must not use http://. Use https:// or wss:// instead."));
                }
              }
            });
    return findings;
  }

  /** {@code owasp-api9-2023-inventory-access}: a server declares {@code x-internal}. */
  static List<OpenApiFinding> inventoryAccess(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final List<DocNode> items = document.parts().servers();
    for (int index = 0; index < items.size(); index++) {
      final DocNode server = document.resolve(items.get(index));
      if (server.kind() == DocNode.Kind.OBJECT && server.get("x-internal").isEmpty()) {
        findings.add(
            Findings.atValue(
                server,
                "Declare intended audience of every server by defining servers["
                    + index
                    + "].x-internal as true/false."));
      }
    }
    return findings;
  }

  /** {@code owasp-api9-2023-inventory-environment}: a server description names its environment. */
  static List<OpenApiFinding> inventoryEnvironment(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode server : servers(document)) {
      final Optional<DocNode> description = server.get("description");
      if (description.isPresent()
          && description.get().kind() == DocNode.Kind.STRING
          && !ENVIRONMENT.matcher(description.get().text()).find()) {
        findings.add(
            Findings.atValue(
                description.get(),
                "Declare intended environment in server descriptions using terms like local,"
                    + " staging, production."));
      }
    }
    return findings;
  }

  private static List<DocNode> servers(final OpenApiDocument document) {
    final List<DocNode> servers = new ArrayList<>();
    for (final DocNode server : document.parts().servers()) {
      final DocNode resolved = document.resolve(server);
      if (resolved.kind() == DocNode.Kind.OBJECT) {
        servers.add(resolved);
      }
    }
    return servers;
  }
}
