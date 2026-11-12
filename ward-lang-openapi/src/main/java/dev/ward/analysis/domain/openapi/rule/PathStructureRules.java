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

/** The rules about how paths, path items and operation parameters are written. */
final class PathStructureRules {

  private static final List<String> METHODS =
      List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
  private static final String IN = "in";
  private static final int MIN_VARIABLE = 2;

  private PathStructureRules() {}

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

  /** {@code no-ambiguous-paths}: no two paths match the same URLs with the same method. */
  static List<OpenApiFinding> ambiguousPaths(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final List<DocNode.Entry> paths = document.parts().paths();
    for (int first = 0; first < paths.size(); first++) {
      for (int second = first + 1; second < paths.size(); second++) {
        findings.addAll(ambiguous(document, paths.get(first), paths.get(second)));
      }
    }
    return findings;
  }

  private static List<OpenApiFinding> ambiguous(
      final OpenApiDocument document, final DocNode.Entry first, final DocNode.Entry second) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final DocNode firstItem = document.resolve(first.value());
    final DocNode secondItem = document.resolve(second.value());
    final List<String> shared = new ArrayList<>(methods(firstItem));
    shared.retainAll(methods(secondItem));
    if (!shared.isEmpty() && sameShape(first.key(), firstItem, second.key(), secondItem)) {
      for (final String method : methods(secondItem)) {
        if (shared.contains(method)) {
          findings.add(
              Findings.in(
                  second.value(),
                  second.keySpan(),
                  "paths are ambiguous with one another: `"
                      + first.key()
                      + "` ("
                      + method.toUpperCase(Locale.ROOT)
                      + ") and `"
                      + second.key()
                      + "` ("
                      + method.toUpperCase(Locale.ROOT)
                      + ")"));
        }
      }
    }
    return findings;
  }

  private static List<String> methods(final DocNode item) {
    return item.entries().stream().map(DocNode.Entry::key).filter(METHODS::contains).toList();
  }

  /** Tells whether two paths have the same segments: same literals, same kind of variables. */
  private static boolean sameShape(
      final String left, final DocNode leftItem, final String right, final DocNode rightItem) {
    final List<String> leftSegments = PathRules.segments(left);
    final List<String> rightSegments = PathRules.segments(right);
    if (leftSegments.size() != rightSegments.size()) {
      return false;
    }
    for (int index = 0; index < leftSegments.size(); index++) {
      final String one = leftSegments.get(index);
      final String other = rightSegments.get(index);
      final boolean oneVariable = isVariable(one);
      if (oneVariable != isVariable(other)) {
        return false;
      }
      if (!oneVariable && !one.equals(other)) {
        return false;
      }
      if (oneVariable && !compatible(one, leftItem, other, rightItem)) {
        return false;
      }
    }
    return true;
  }

  private static boolean compatible(
      final String left, final DocNode leftItem, final String right, final DocNode rightItem) {
    final String leftType = parameterType(leftItem, name(left));
    final String rightType = parameterType(rightItem, name(right));
    return operator(left).equals(operator(right))
        && (leftType.isEmpty()
            || rightType.isEmpty()
            || leftType.equals(rightType)
            || Set.of(leftType, rightType).equals(Set.of("integer", "number")));
  }

  private static boolean isVariable(final String segment) {
    return segment.length() > MIN_VARIABLE && segment.startsWith("{") && segment.endsWith("}");
  }

  private static String operator(final String segment) {
    final String inner = segment.substring(1, segment.length() - 1);
    return !inner.isEmpty() && "+#./;?&".indexOf(inner.charAt(0)) >= 0 ? inner.substring(0, 1) : "";
  }

  private static String name(final String segment) {
    String inner = segment.substring(1, segment.length() - 1);
    inner = operator(segment).isEmpty() ? inner : inner.substring(1);
    inner = inner.endsWith("*") ? inner.substring(0, inner.length() - 1) : inner;
    final int colon = inner.lastIndexOf(':');
    return colon >= 0 && inner.substring(colon + 1).matches("[1-9][0-9]*")
        ? inner.substring(0, colon)
        : inner;
  }

  /** The first declared type of the path parameter, at the path item or in an operation. */
  private static String parameterType(final DocNode item, final String name) {
    final List<DocNode> parameters = new ArrayList<>(OperationNodes.parameters(item));
    for (final DocNode.Entry method : item.entries()) {
      if (METHODS.contains(method.key())) {
        parameters.addAll(OperationNodes.parameters(method.value()));
      }
    }
    for (final DocNode parameter : parameters) {
      final boolean match =
          "path".equals(parameter.string(IN).orElse(""))
              && name.equals(parameter.string("name").orElse(""));
      final String type =
          parameter.get("schema").flatMap(schema -> schema.string("type")).orElse("");
      if (match && !type.isEmpty()) {
        return type;
      }
    }
    return "";
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
    final boolean bodyLike = "body".equals(in) || "formData".equals(in);
    if (seenIn.contains(in)) {
      if ("body".equals(in)) {
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
      if (bodyLike && (seenIn.contains("formData") || seenIn.contains("body"))) {
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
