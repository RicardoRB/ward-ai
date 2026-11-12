package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The rule that finds a reusable component nothing refers to. */
final class UnusedComponentRules {

  private static final List<String> SECTIONS =
      List.of(
          "schemas",
          "responses",
          "parameters",
          "examples",
          "requestBodies",
          "headers",
          "links",
          "callbacks");

  private UnusedComponentRules() {}

  /** {@code oas3-unused-component}: a component is the target of some {@code $ref}. */
  static List<OpenApiFinding> unused(final OpenApiDocument document) {
    final Set<String> targets = new HashSet<>();
    for (final DocNode file : document.files().values()) {
      for (final Nodes.Located located : Nodes.raw(file)) {
        document
            .target(located.node())
            .ifPresent(target -> targets.add(target.file() + "#" + target.pointer()));
      }
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final String section : SECTIONS) {
      for (final DocNode.Entry component : document.parts().component(section)) {
        if (!targets.contains(component.value().file() + "#" + component.value().pointer())) {
          findings.add(
              Findings.atValue(
                  component.value(), "Potentially unused component has been detected."));
        }
      }
    }
    return findings;
  }
}
