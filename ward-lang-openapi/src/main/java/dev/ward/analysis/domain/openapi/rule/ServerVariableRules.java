package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code oas3-server-variables}: the variables of a server URL are defined, used and valid. */
final class ServerVariableRules {

  private static final Pattern VARIABLE = Pattern.compile("\\{(.+?)\\}");
  private static final String VARIABLES = "variables";
  private static final String DEFAULT = "default";
  private static final int INVALID_LIMIT = 5;
  private static final String INVALID = "Invalid Server Object";

  private ServerVariableRules() {}

  /** Checks every server object: of the document, a path item, an operation and a link. */
  static List<OpenApiFinding> serverVariables(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode server : document.parts().servers()) {
      check(document, document.resolve(server), findings);
    }
    for (final DocNode.Entry path : document.parts().paths()) {
      servers(document, document.resolve(path.value()), findings);
    }
    for (final Operation operation : document.parts().operations()) {
      servers(document, operation.node(), findings);
    }
    return findings;
  }

  private static void servers(
      final OpenApiDocument document, final DocNode owner, final List<OpenApiFinding> findings) {
    document
        .get(owner, "servers", View.RESOLVED)
        .filter(list -> list.kind() == DocNode.Kind.ARRAY)
        .ifPresent(
            list ->
                list.items().forEach(item -> check(document, document.resolve(item), findings)));
  }

  private static void check(
      final OpenApiDocument document, final DocNode server, final List<OpenApiFinding> findings) {
    final Optional<DocNode> url = server.get("url");
    if (server.kind() != DocNode.Kind.OBJECT
        || url.isEmpty()
        || url.get().kind() != DocNode.Kind.STRING) {
      findings.add(Findings.atValue(server, INVALID));
      return;
    }
    final List<String> found = new ArrayList<>();
    final Matcher matcher = VARIABLE.matcher(url.get().text());
    while (matcher.find()) {
      found.add(matcher.group(1));
    }
    final Optional<DocNode> variables =
        document
            .get(server, VARIABLES, View.RESOLVED)
            .filter(node -> node.kind() == DocNode.Kind.OBJECT);
    final List<String> defined =
        variables
            .map(node -> node.entries().stream().map(DocNode.Entry::key).toList())
            .orElse(List.of());
    for (final String name : defined) {
      if (!found.contains(name)) {
        findings.add(
            Findings.atValue(
                variables.orElseThrow().get(name).orElseThrow(),
                "Server's \"variables\" object has unused defined \"" + name + "\" url variable."));
      }
    }
    if (found.isEmpty()) {
      return;
    }
    final List<String> missing = found.stream().filter(name -> !defined.contains(name)).toList();
    if (!missing.isEmpty()) {
      findings.add(
          Findings.atValue(
              variables.orElse(server),
              "Not all server's variables are described with \"variables\" object. Missed: "
                  + String.join(", ", missing)
                  + "."));
    }
    variables.ifPresent(node -> described(node, found, url.get().text(), findings));
  }

  private static void described(
      final DocNode variables,
      final List<String> found,
      final String url,
      final List<OpenApiFinding> findings) {
    final Map<String, List<String>> pairs = new LinkedHashMap<>();
    for (final DocNode.Entry entry : variables.entries()) {
      if (!found.contains(entry.key()) || entry.value().kind() != DocNode.Kind.OBJECT) {
        continue;
      }
      final Optional<DocNode> values = entry.value().get("enum");
      final Optional<DocNode> fallback = entry.value().get(DEFAULT);
      if (values.isPresent()) {
        pairs.put(entry.key(), values.get().items().stream().map(DocNode::text).toList());
        if (fallback.isPresent()
            && values.get().items().stream()
                .noneMatch(item -> item.text().equals(fallback.get().text()))) {
          findings.add(
              Findings.atValue(
                  fallback.get(),
                  "Server Variable \"" + entry.key() + "\" has a default not listed in the enum."));
        }
      } else {
        pairs.put(entry.key(), fallback.map(node -> List.of(node.text())).orElse(List.of()));
      }
      if (fallback.isEmpty()) {
        findings.add(
            Findings.atValue(
                entry.value(), "Server Variable \"" + entry.key() + "\" has a missing default."));
      }
    }
    substitutions(variables, url, pairs, findings);
  }

  private static void substitutions(
      final DocNode variables,
      final String url,
      final Map<String, List<String>> pairs,
      final List<OpenApiFinding> findings) {
    if (pairs.isEmpty() || pairs.values().stream().anyMatch(List::isEmpty)) {
      return;
    }
    final List<String> invalid = new ArrayList<>();
    expand(url, new ArrayList<>(pairs.entrySet()), 0, invalid);
    if (invalid.size() >= INVALID_LIMIT) {
      findings.add(
          Findings.atValue(
              variables,
              "At least 5 substitutions of server variables resulted in invalid URLs: "
                  + String.join(", ", invalid.subList(0, INVALID_LIMIT))
                  + " and more"));
    } else if (!invalid.isEmpty()) {
      findings.add(
          Findings.atValue(
              variables,
              "A few substitutions of server variables resulted in invalid URLs: "
                  + String.join(", ", invalid)));
    }
  }

  private static void expand(
      final String url,
      final List<Map.Entry<String, List<String>>> pairs,
      final int index,
      final List<String> invalid) {
    final Map.Entry<String, List<String>> pair = pairs.get(index);
    for (final String value : pair.getValue()) {
      if (invalid.size() >= INVALID_LIMIT) {
        return;
      }
      final String substituted = url.replace("{" + pair.getKey() + "}", encode(value));
      if (index == pairs.size() - 1) {
        if (!validUrl(substituted)) {
          invalid.add(substituted);
        }
      } else {
        expand(substituted, pairs, index + 1, invalid);
      }
    }
  }

  private static String encode(final String value) {
    return value.replace(" ", "%20");
  }

  /**
   * Tells whether the WHATWG URL parser accepts the text.
   *
   * <p>shortcut: an absolute URI with a host for the special schemes; upgrade when a URL that
   * differs between {@link URI} and the WHATWG parser matters.
   */
  private static boolean validUrl(final String text) {
    try {
      final URI uri = new URI(text.replaceAll("[{}|\\\\^`\" <>]", "%20"));
      if (uri.getScheme() == null) {
        return false;
      }
      final boolean special =
          List.of("http", "https", "ws", "wss", "ftp").contains(uri.getScheme());
      return !special || uri.getHost() != null && !uri.getHost().isEmpty();
    } catch (final URISyntaxException exception) {
      return false;
    }
  }
}
