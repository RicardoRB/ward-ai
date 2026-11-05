package dev.ward.application.init;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.QualityGatePhasePolicy;
import java.util.List;
import java.util.stream.Collectors;

/** Writes the {@code ward} gate that enforces the architecture tables of a selection. */
final class WardGateTemplate {

  private WardGateTemplate() {}

  /**
   * Appends the gate, one {@code [[checks.ward.layers]]} table per boundary, then the cycle and
   * independence tables that {@code architecture} sets.
   */
  static void append(final StringBuilder content, final EngineArchitecture architecture) {
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
    for (final LayerBoundary boundary : architecture.layers()) {
      content
          .append("\n[[checks.ward.layers]]\nfrom = ")
          .append(names(boundary.from()))
          .append("\nforbidden = ")
          .append(names(boundary.forbidden()))
          .append('\n');
    }
    if (!architecture.cycleRoots().isEmpty()) {
      content
          .append("\n[checks.ward.cycles]\nroots = ")
          .append(names(architecture.cycleRoots()))
          .append('\n');
    }
    if (!architecture.independence().isEmpty()) {
      content
          .append("\n[[checks.ward.independence]]\nmodules = ")
          .append(names(architecture.independence()))
          .append('\n');
    }
  }

  private static String names(final List<String> names) {
    return names.stream()
        .map(name -> "\"" + name + "\"")
        .collect(Collectors.joining(", ", "[", "]"));
  }
}
