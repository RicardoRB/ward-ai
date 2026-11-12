package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import dev.ward.analysis.domain.openapi.OperationNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The rules about descriptions of operations, components and parameters. */
final class DescriptionRules {

  private static final String DESCRIPTION = "description";
  private static final List<String> COMPONENT_SECTIONS =
      List.of(
          "schemas",
          "parameters",
          "requestBodies",
          "responses",
          "examples",
          "headers",
          "links",
          "securitySchemes");

  private DescriptionRules() {}

  /** {@code operation-description}: operations, request bodies and responses are described. */
  static List<OpenApiFinding> operation(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      final String method = operation.method().toUpperCase(Locale.ROOT);
      final String at = "at path `" + operation.path() + "`";
      final DocNode node = operation.node();
      if (Findings.text(node, DESCRIPTION).isEmpty() && Findings.text(node, "summary").isEmpty()) {
        findings.add(
            Findings.at(
                node,
                "operation method `"
                    + method
                    + "` "
                    + at
                    + " is missing a description or summary"));
      }
      OperationNodes.requestBody(node)
          .map(document::resolve)
          .filter(body -> Findings.text(body, DESCRIPTION).isEmpty())
          .ifPresent(
              body ->
                  findings.add(
                      Findings.in(
                          body,
                          node.get("requestBody").orElseThrow().anchor(),
                          "operation method `"
                              + method
                              + "` `requestBody` "
                              + at
                              + " is missing a `description`")));
      for (final DocNode.Entry response : OperationNodes.responses(node)) {
        if (Findings.text(document.resolve(response.value()), DESCRIPTION).isEmpty()) {
          findings.add(
              Findings.in(
                  response.value(),
                  response.keySpan(),
                  "operation method `"
                      + method
                      + "` response code `"
                      + response.key()
                      + "` `responseBody` "
                      + at
                      + " is missing a `description`"));
        }
      }
    }
    return findings;
  }

  /** {@code component-description}: the reusable components are described. */
  static List<OpenApiFinding> component(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final String section : COMPONENT_SECTIONS) {
      for (final DocNode.Entry entry : document.parts().component(section)) {
        if (Findings.text(document.resolve(entry.value()), DESCRIPTION).isEmpty()) {
          findings.add(
              Findings.in(
                  entry.value(),
                  entry.keySpan(),
                  "`" + section + "` component `" + entry.key() + "` is missing a description"));
        }
      }
    }
    return findings;
  }

  /** {@code oas3-parameter-description}: component, path and operation parameters are described. */
  static List<OpenApiFinding> parameter(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry entry : document.parts().component("parameters")) {
      final DocNode parameter = document.resolve(entry.value());
      if (Findings.text(parameter, DESCRIPTION).isEmpty()) {
        findings.add(Findings.atValue(parameter, missing(entry.key())));
      }
    }
    for (final DocNode.Entry path : document.parts().paths()) {
      final DocNode item = document.resolve(path.value());
      parameters(document, item, findings);
    }
    for (final Operation operation : document.parts().operations()) {
      parameters(document, operation.node(), findings);
    }
    return findings;
  }

  private static void parameters(
      final OpenApiDocument document, final DocNode owner, final List<OpenApiFinding> findings) {
    final List<DocNode> parameters = OperationNodes.parameters(owner);
    for (int index = 0; index < parameters.size(); index++) {
      final DocNode parameter = document.resolve(parameters.get(index));
      if (Findings.text(parameter, DESCRIPTION).isEmpty()) {
        final String name = Findings.text(parameter, "name");
        findings.add(
            Findings.atValue(
                parameter, missing(name.isEmpty() ? "parameter[" + index + "]" : name)));
      }
    }
  }

  private static String missing(final String name) {
    return "the parameter `" + name + "` does not contain a description";
  }
}
