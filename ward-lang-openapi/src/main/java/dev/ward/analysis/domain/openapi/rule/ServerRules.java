package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The rules about {@code servers}: they exist, avoid example hosts and trailing slashes. */
final class ServerRules {

  private static final Pattern EXAMPLE = Pattern.compile("example\\.com");
  private static final Pattern TRAILING_SLASH = Pattern.compile("./\\z");
  private static final String SERVERS_MESSAGE =
      "OpenAPI \"servers\" must be present and non-empty array.";

  private ServerRules() {}

  /** {@code oas3-api-servers}: {@code servers} is a non-empty array of objects. */
  static List<OpenApiFinding> apiServers(final OpenApiDocument document) {
    final Optional<DocNode> servers = document.get(document.root(), "servers", View.RESOLVED);
    if (servers.isEmpty()) {
      return List.of(Findings.atValue(document.root(), SERVERS_MESSAGE));
    }
    final DocNode node = servers.get();
    if (node.kind() != DocNode.Kind.ARRAY || node.items().isEmpty()) {
      return List.of(Findings.atValue(node, SERVERS_MESSAGE));
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode item : node.items()) {
      final DocNode server = document.resolve(item);
      if (server.kind() != DocNode.Kind.OBJECT) {
        findings.add(Findings.atValue(server, SERVERS_MESSAGE));
      }
    }
    return findings;
  }

  /** {@code oas3-server-not-example-com}: no server URL points at {@code example.com}. */
  static List<OpenApiFinding> notExample(final OpenApiDocument document) {
    return urlFindings(document, EXAMPLE, "Server URL must not point at example.com.");
  }

  /** {@code oas3-server-trailing-slash}: no server URL ends with a slash after a character. */
  static List<OpenApiFinding> trailingSlash(final OpenApiDocument document) {
    return urlFindings(document, TRAILING_SLASH, "Server URL must not have trailing slash.");
  }

  private static List<OpenApiFinding> urlFindings(
      final OpenApiDocument document, final Pattern pattern, final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode server : document.parts().servers()) {
      document
          .get(server, "url", View.RESOLVED)
          .filter(url -> url.kind() == DocNode.Kind.STRING)
          .filter(url -> pattern.matcher(url.text()).find())
          .ifPresent(url -> findings.add(Findings.atValue(url, message)));
    }
    return findings;
  }
}
