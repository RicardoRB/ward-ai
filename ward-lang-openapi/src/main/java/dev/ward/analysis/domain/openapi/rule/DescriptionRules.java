package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rule that wants a description on every parameter. */
final class DescriptionRules {

  private static final String PARAMETERS = "parameters";

  private DescriptionRules() {}

  /**
   * {@code oas3-parameter-description}: a parameter of a path item, an operation or the components.
   */
  static List<OpenApiFinding> parameter(final OpenApiDocument document) {
    final List<DocNode> owners = new ArrayList<>();
    for (final DocNode.Entry path : document.parts().paths()) {
      owners.add(document.resolve(path.value()));
    }
    for (final Operation operation : document.parts().operations()) {
      owners.add(operation.node());
    }
    final List<DocNode> parameters = new ArrayList<>();
    for (final DocNode owner : owners) {
      document
          .get(owner, PARAMETERS, View.RESOLVED)
          .filter(list -> list.kind() == DocNode.Kind.ARRAY)
          .ifPresent(list -> list.items().forEach(item -> parameters.add(document.resolve(item))));
    }
    document
        .parts()
        .component(PARAMETERS)
        .forEach(entry -> parameters.add(document.resolve(entry.value())));
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode parameter : parameters) {
      final Optional<DocNode> in = parameter.get("in");
      if (parameter.kind() == DocNode.Kind.OBJECT && in.isPresent() && Fields.truthy(in.get())) {
        findings.addAll(
            Fields.requireTruthy(
                document,
                parameter,
                "Parameter objects must have \"description\".",
                "description"));
      }
    }
    return findings;
  }
}
