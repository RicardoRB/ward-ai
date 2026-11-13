package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** The rules about each operation: description, operationId, responses and parameters. */
final class OperationRules {

  private static final String OPERATION_ID = "operationId";
  private static final String BODY = "body";
  private static final String FORM_DATA = "formData";
  private static final Pattern URL_SAFE =
      Pattern.compile("^[A-Za-z0-9-._~:/?#\\[\\]@!\\$&'()*+,;=]*\\z");
  private static final double FIRST_SUCCESS = 200;
  private static final double AFTER_REDIRECT = 400;
  private static final int PAIR = 2;

  private OperationRules() {}

  /** {@code operation-description}: every operation has a non-empty description. */
  static List<OpenApiFinding> description(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      findings.addAll(
          Fields.requireTruthy(
              document,
              operation.node(),
              "Operation \"description\" must be present and non-empty string.",
              "description"));
    }
    return findings;
  }

  /** {@code operation-operation-id}: every operation has an operationId. */
  static List<OpenApiFinding> operationId(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      findings.addAll(
          Fields.requireTruthy(
              document, operation.node(), "Operation must have \"operationId\".", OPERATION_ID));
    }
    return findings;
  }

  /** {@code operation-operation-id-valid-in-url}: an operationId has URL-safe characters only. */
  static List<OpenApiFinding> validInUrl(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      document
          .get(operation.node(), OPERATION_ID, View.RESOLVED)
          .filter(id -> id.kind() == DocNode.Kind.STRING && !URL_SAFE.matcher(id.text()).find())
          .ifPresent(
              id ->
                  findings.add(
                      Findings.atValue(
                          id,
                          "operationId must not contain characters that are invalid when used in"
                              + " URL.")));
    }
    return findings;
  }

  /** {@code operation-operation-id-unique}: an operationId is not used twice. */
  static List<OpenApiFinding> unique(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    for (final Operation operation : document.parts().operations()) {
      final Optional<DocNode> id = document.get(operation.node(), OPERATION_ID, View.RESOLVED);
      if (id.isPresent() && !seen.add(id.get().kind() + ":" + id.get().text())) {
        findings.add(
            Findings.atValue(id.get(), "Every operation must have unique \"operationId\"."));
      }
    }
    return findings;
  }

  /** {@code operation-success-response}: an operation has a 2xx or 3xx response. */
  static List<OpenApiFinding> successResponse(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      document
          .get(operation.node(), "responses", View.RESOLVED)
          .filter(responses -> responses.kind() == DocNode.Kind.OBJECT)
          .filter(responses -> responses.entries().stream().noneMatch(OperationRules::success))
          .ifPresent(
              responses ->
                  findings.add(
                      Findings.atValue(
                          responses,
                          "Operation must have at least one \"2xx\" or \"3xx\" response.")));
    }
    return findings;
  }

  private static boolean success(final DocNode.Entry response) {
    final String key = response.key();
    if ("2XX".equals(key) || "3XX".equals(key)) {
      return true;
    }
    final double code = JavaScript.number(key);
    return code >= FIRST_SUCCESS && code < AFTER_REDIRECT;
  }

  /** {@code operation-parameters}: an operation never repeats a name and location. */
  static List<OpenApiFinding> parameters(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      final Optional<DocNode> parameters =
          document.get(operation.node(), "parameters", View.RESOLVED);
      if (parameters.isPresent() && parameters.get().kind() == DocNode.Kind.ARRAY) {
        findings.addAll(duplicates(document, parameters.get()));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> duplicates(
      final OpenApiDocument document, final DocNode parameters) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    if (parameters.items().size() < PAIR) {
      return findings;
    }
    final Set<String> seen = new HashSet<>();
    final List<DocNode> body = new ArrayList<>();
    final List<DocNode> form = new ArrayList<>();
    for (final DocNode item : parameters.items()) {
      final DocNode parameter = document.resolve(item);
      if (parameter.kind() != DocNode.Kind.OBJECT || parameter.reference().isPresent()) {
        continue;
      }
      final String fingerprint =
          JavaScript.text(parameter, "in") + "-" + JavaScript.text(parameter, "name");
      if (!seen.add(fingerprint)) {
        findings.add(
            Findings.atValue(
                parameter,
                "A parameter in this operation already exposes the same combination of \"name\""
                    + " and \"in\" values."));
      }
      collect(parameter, body, form);
    }
    findings.addAll(bodyProblems(parameters, body, form));
    return findings;
  }

  private static void collect(
      final DocNode parameter, final List<DocNode> body, final List<DocNode> form) {
    final Optional<DocNode> in = parameter.get("in");
    if (in.isPresent() && in.get().kind() == DocNode.Kind.STRING) {
      if (BODY.equals(in.get().text())) {
        body.add(parameter);
      } else if (FORM_DATA.equals(in.get().text())) {
        form.add(parameter);
      }
    }
  }

  private static List<OpenApiFinding> bodyProblems(
      final DocNode parameters, final List<DocNode> body, final List<DocNode> form) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    if (!body.isEmpty() && !form.isEmpty()) {
      findings.add(
          Findings.atValue(
              parameters,
              "Operation must not have both \"in:body\" and \"in:formData\" parameters."));
    }
    for (int index = 1; index < body.size(); index++) {
      findings.add(
          Findings.atValue(
              body.get(index),
              "Operation must not have more than a single instance of the \"in:body\" parameter."));
    }
    return findings;
  }
}
