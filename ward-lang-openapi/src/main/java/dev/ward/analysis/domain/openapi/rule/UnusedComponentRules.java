package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@code oas3-unused-component}: every reusable component is referenced somewhere. */
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

  /** Reports the components no {@code $ref} of the loaded files points at. */
  static List<OpenApiFinding> unused(final OpenApiDocument document) {
    final Set<String> referenced = new HashSet<>();
    for (final DocNode root : document.files().values()) {
      collect(root, referenced);
    }
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final String section : SECTIONS) {
      for (final DocNode.Entry component : document.parts().component(section)) {
        final String reference = "#/components/" + section + "/" + component.key();
        if (!referenced.contains(reference)) {
          findings.add(
              Findings.in(
                  component.value(),
                  component.keySpan(),
                  "`" + reference + "` is potentially unused or has been orphaned"));
        }
      }
    }
    return findings;
  }

  private static void collect(final DocNode node, final Set<String> referenced) {
    node.reference().ifPresent(referenced::add);
    node.children().forEach(child -> collect(child, referenced));
  }
}
