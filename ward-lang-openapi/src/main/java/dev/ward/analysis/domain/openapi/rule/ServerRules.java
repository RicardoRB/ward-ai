package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The rules about {@code servers}: they exist, have usable URLs and avoid example hosts. */
final class ServerRules {

  private static final Pattern URL =
      Pattern.compile("^(?:([A-Za-z][A-Za-z0-9+.-]*):)?(?://([^/?#]*))?([^?#]*)");
  private static final Pattern EXAMPLE = Pattern.compile("example\\.com");

  private static final int HOST_GROUP = 2;
  private static final int PATH_GROUP = 3;

  private ServerRules() {}

  /** {@code oas3-api-servers}: the document defines servers, each with a parsable URL. */
  static List<OpenApiFinding> apiServers(final OpenApiDocument document) {
    final DocNode root = document.root();
    final Optional<DocNode> servers = root.get("servers");
    if (servers.isEmpty()) {
      return List.of(
          Findings.in(
              root,
              root.entries().getFirst().keySpan(),
              "no servers defined for the specification"));
    }
    if (servers.get().items().isEmpty()) {
      return List.of(
          Findings.atValue(servers.get(), "servers definition is empty, contains no servers!"));
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode server : servers.get().items()) {
      server(document.resolve(server)).ifPresent(findings::add);
    }
    return findings;
  }

  /** {@code oas3-host-not-example}: a server URL does not point at {@code example.com}. */
  static List<OpenApiFinding> hostNotExample(final OpenApiDocument document) {
    return urls(document).stream()
        .filter(url -> EXAMPLE.matcher(url.text()).find())
        .map(
            url ->
                Findings.atValue(
                    url,
                    "Server URL should not point at example.com: matches the expression"
                        + " `example\\.com`"))
        .toList();
  }

  /** {@code oas3-server-trailing-slash}: a server URL does not end with a slash. */
  static List<OpenApiFinding> trailingSlash(final OpenApiDocument document) {
    return urls(document).stream()
        .filter(url -> url.text().endsWith("/"))
        .map(
            url ->
                Findings.atValue(
                    url,
                    "server URL should not contain a trailing slash: matches the expression"
                        + " `/$`"))
        .toList();
  }

  private static List<DocNode> urls(final OpenApiDocument document) {
    final List<DocNode> urls = new ArrayList<>();
    for (final DocNode server : document.parts().servers()) {
      document
          .resolve(server)
          .get("url")
          .filter(url -> url.kind() == DocNode.Kind.STRING)
          .ifPresent(urls::add);
    }
    return urls;
  }

  private static Optional<OpenApiFinding> server(final DocNode server) {
    final Optional<DocNode> url = server.get("url");
    if (url.isEmpty()) {
      return Optional.of(Findings.atValue(server, "server definition is missing a URL"));
    }
    final String text = url.get().text();
    if (text.contains("{") && text.contains("}") || text.isEmpty() || "/".equals(text)) {
      return Optional.empty();
    }
    return urlProblem(text).map(message -> Findings.in(server, keyOf(server), message));
  }

  private static Span keyOf(final DocNode server) {
    return server.entries().stream()
        .filter(entry -> "url".equals(entry.key()))
        .findFirst()
        .orElseThrow()
        .keySpan();
  }

  /** What is wrong with a server URL, as vacuum words it. */
  private static Optional<String> urlProblem(final String text) {
    if (text.startsWith(":")) {
      return Optional.of(
          "server URL cannot be parsed: parse \"" + text + "\": missing protocol scheme");
    }
    final Matcher parts = URL.matcher(text);
    final boolean found = parts.find();
    final String host = found && parts.group(HOST_GROUP) != null ? parts.group(HOST_GROUP) : "";
    final String path = found ? parts.group(PATH_GROUP) : "";
    if (host.isEmpty() && path.isEmpty()) {
      return Optional.of("Server URL is not valid: no hostname or path provided");
    }
    if (path.endsWith("/")) {
      return Optional.of("Server URL is not valid: must not have a trailing slash");
    }
    return Optional.empty();
  }
}
