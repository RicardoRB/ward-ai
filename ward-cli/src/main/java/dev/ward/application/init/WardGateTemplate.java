package dev.ward.application.init;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.QualityGatePhasePolicy;
import java.util.List;
import java.util.stream.Collectors;

/** Writes the {@code ward} gate that enforces the layer boundaries of an architecture style. */
final class WardGateTemplate {

  private WardGateTemplate() {}

  /** Appends the gate and one {@code [[checks.ward.layers]]} table per boundary. */
  static void append(final StringBuilder content, final List<LayerBoundary> layers) {
    content
        .append("\n[checks.ward]\nenabled = true\nprofiles = [\"")
        .append(QualityGatePhasePolicy.DEFAULT)
        .append("\", \"")
        .append(QualityGatePhasePolicy.PRE_COMMIT)
        .append("\", \"")
        .append(QualityGatePhasePolicy.PRE_PUSH)
        .append("\", \"")
        .append(QualityGatePhasePolicy.CI)
        .append("\", \"")
        .append(QualityGatePhasePolicy.EDIT)
        .append("\"]\nuses = \"ward\"\n");
    for (final LayerBoundary boundary : layers) {
      content
          .append("\n[[checks.ward.layers]]\nfrom = ")
          .append(names(boundary.from()))
          .append("\nforbidden = ")
          .append(names(boundary.forbidden()))
          .append('\n');
    }
  }

  private static String names(final List<String> names) {
    return names.stream()
        .map(name -> "\"" + name + "\"")
        .collect(Collectors.joining(", ", "[", "]"));
  }
}
