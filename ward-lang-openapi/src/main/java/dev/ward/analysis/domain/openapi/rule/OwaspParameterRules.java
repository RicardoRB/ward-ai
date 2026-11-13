package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The OWASP rules about parameters: guessable ids, credentials in URLs and URL parameters. */
final class OwaspParameterRules {

  private static final String PARAMETERS = "parameters";
  private static final Pattern ID_NAME = Pattern.compile("(_id|Id|-id)\\z");
  private static final Pattern CREDENTIAL =
      Pattern.compile(
          "^.*(client_?secret|token|access_?token|refresh_?token|id_?token|password|secret|api-?key).*$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern URL_NAME =
      Pattern.compile("(^(callback|redirect)|(_url|Url|-url))\\z");

  private OwaspParameterRules() {}

  /** {@code owasp-api1-2023-no-numeric-ids}: an id parameter is not an integer schema. */
  static List<OpenApiFinding> noNumericIds(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode parameter : parametersUnderPaths(document)) {
      final Optional<DocNode> name = parameter.get("name");
      final Optional<DocNode> schema = document.get(parameter, "schema", View.RESOLVED);
      if (name.isPresent()
          && name.get().kind() == DocNode.Kind.STRING
          && ("id".equals(name.get().text()) || ID_NAME.matcher(name.get().text()).find())
          && schema.isPresent()
          && integerOrUntyped(schema.get())) {
        findings.add(
            Findings.atValue(
                schema.get(),
                "Use random IDs that cannot be guessed. UUIDs are preferred but any other random"
                    + " string will do."));
      }
    }
    return findings;
  }

  /** The schema of the rule rejects an object without a {@code type}, which matches vacuously. */
  private static boolean integerOrUntyped(final DocNode schema) {
    return schema.kind() != DocNode.Kind.OBJECT
        || schema.get("type").map(type -> "integer".equals(type.text())).orElse(true);
  }

  /** {@code owasp-api2-2023-no-credentials-in-url}: no query or path parameter is a secret. */
  static List<OpenApiFinding> noCredentialsInUrl(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode parameter : parameters(document, document.root())) {
      final Optional<DocNode> in = parameter.get("in");
      final Optional<DocNode> name = parameter.get("name");
      if (in.isPresent()
          && in.get().kind() == DocNode.Kind.STRING
          && (in.get().text().contains("query") || in.get().text().contains("path"))
          && name.isPresent()
          && name.get().kind() == DocNode.Kind.STRING
          && CREDENTIAL.matcher(name.get().text()).find()) {
        findings.add(
            Findings.atValue(
                name.get(),
                "Security credentials detected in path parameter: " + name.get().text() + "."));
      }
    }
    return findings;
  }

  /** {@code owasp-api7-2023-concerning-url-parameter}: a parameter name suggests a URL. */
  static List<OpenApiFinding> concerningUrlParameter(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final List<DocNode> owners = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      owners.add(document.resolve(path.value()));
    }
    for (final Operation operation : document.parts().operations()) {
      owners.add(operation.node());
    }
    for (final DocNode owner : owners) {
      for (final DocNode parameter : listed(document, owner)) {
        parameter
            .get("name")
            .filter(name -> name.kind() == DocNode.Kind.STRING)
            .filter(name -> URL_NAME.matcher(name.text()).find())
            .ifPresent(
                name ->
                    findings.add(
                        Findings.atValue(
                            name,
                            "Make sure to review the way this URL is handled to protect against"
                                + " Server Side Request Forgery.")));
      }
    }
    return findings;
  }

  private static List<DocNode> listed(final OpenApiDocument document, final DocNode owner) {
    final List<DocNode> parameters = new ArrayList<>();
    document
        .get(owner, PARAMETERS, View.RESOLVED)
        .filter(list -> list.kind() == DocNode.Kind.ARRAY)
        .ifPresent(list -> list.items().forEach(item -> parameters.add(document.resolve(item))));
    return parameters.stream()
        .filter(parameter -> parameter.kind() == DocNode.Kind.OBJECT)
        .toList();
  }

  private static List<DocNode> parametersUnderPaths(final OpenApiDocument document) {
    final List<DocNode> found = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      found.addAll(parameters(document, document.resolve(path.value())));
    }
    return found;
  }

  /** The children of every {@code parameters} key at or below {@code start}. */
  private static List<DocNode> parameters(final OpenApiDocument document, final DocNode start) {
    final List<DocNode> found = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, start)) {
      if (PARAMETERS.equals(located.key())) {
        for (final DocNode child : located.node().children()) {
          final DocNode parameter = document.resolve(child);
          if (parameter.kind() == DocNode.Kind.OBJECT) {
            found.add(parameter);
          }
        }
      }
    }
    return found;
  }
}
