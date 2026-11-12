package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import dev.ward.analysis.domain.openapi.OperationNodes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The rules about how operations and their parameters are written. */
final class OperationStructureRules {

  private static final List<String> METHODS =
      List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
  private static final String IN = "in";
  private static final String BODY = "body";
  private static final String FORM_DATA = "formData";

  private OperationStructureRules() {}

  /** {@code path-item-refs}: only a path item, never an operation, is a {@code $ref}. */
  static List<OpenApiFinding> pathItemRefs(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      for (final DocNode.Entry method : document.resolve(path.value()).entries()) {
        if (METHODS.contains(method.key()) && method.value().reference().isPresent()) {
          findings.add(
              Findings.in(
                  method.value(),
                  method.keySpan(),
                  "`"
                      + method.key().toUpperCase(Locale.ROOT)
                      + "` operation at path `"
                      + path.key()
                      + "` uses a $ref, which is not valid; $ref is only allowed on the path item"
                      + " itself"));
        }
      }
    }
    return findings;
  }

  /** {@code operation-parameters}: parameters have an {@code in} and are not repeated. */
  static List<OpenApiFinding> operationParameters(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Operation operation : document.parts().operations()) {
      findings.addAll(parametersOf(document, operation));
    }
    return findings;
  }

  private static List<OpenApiFinding> parametersOf(
      final OpenApiDocument document, final Operation operation) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final String method = operation.method().toUpperCase(Locale.ROOT);
    final String at = "at path `" + operation.path() + "`";
    final Set<String> seenIn = new HashSet<>();
    final Set<String> seenIdentity = new HashSet<>();
    final List<DocNode> parameters = OperationNodes.parameters(operation.node());
    for (int index = 0; index < parameters.size(); index++) {
      final DocNode raw = parameters.get(index);
      final DocNode parameter = document.resolve(raw);
      final String reference = raw.reference().orElse("");
      final String in = parameter.string(IN).orElse("");
      final String identity =
          reference.isEmpty() ? parameter.string("name").orElse("") + "\u0000" + in : reference;
      if (!seenIdentity.add(identity) && !(reference.isEmpty() && in.isEmpty())) {
        final String what =
            reference.isEmpty()
                ? "name `" + parameter.string("name").orElse("") + "` and `in` type"
                : "ref `" + reference + "`";
        findings.add(
            Findings.atValue(
                raw,
                "the `"
                    + method
                    + "` operation parameter "
                    + at
                    + ", index "
                    + index
                    + " has a duplicate "
                    + what));
      }
      if (in.isEmpty()) {
        findings.add(
            Findings.atValue(
                parameter,
                "the `"
                    + method
                    + "` operation "
                    + at
                    + " contains a parameter with no `in` value"));
      } else {
        findings.addAll(bodyProblems(parameter, in, seenIn, method, at));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> bodyProblems(
      final DocNode parameter,
      final String in,
      final Set<String> seenIn,
      final String method,
      final String at) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final boolean bodyLike = BODY.equals(in) || FORM_DATA.equals(in);
    if (seenIn.contains(in)) {
      if (BODY.equals(in)) {
        findings.add(
            Findings.atValue(
                parameter,
                "the `"
                    + method
                    + "` operation "
                    + at
                    + " contains a duplicate param in:body definition"));
      }
    } else {
      if (bodyLike && (seenIn.contains(FORM_DATA) || seenIn.contains(BODY))) {
        findings.add(
            Findings.atValue(
                parameter,
                "the `"
                    + method
                    + "` operation "
                    + at
                    + " contains parameters using both in:body and in:formData"));
      }
      seenIn.add(in);
    }
    return findings;
  }
}
