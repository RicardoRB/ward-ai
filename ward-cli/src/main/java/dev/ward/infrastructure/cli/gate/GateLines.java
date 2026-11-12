package dev.ward.infrastructure.cli.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateSection;
import dev.ward.domain.gate.GateStatus;
import java.util.stream.Collectors;

/** The text lines of one finished gate. */
final class GateLines {
  private GateLines() {}

  /**
   * One line per section when a gate that passed or failed checked several things, otherwise the
   * single {@link ReportText#gateLine(GateResult)}.
   */
  static String of(final GateResult result) {
    final boolean ran =
        result.status() == GateStatus.PASSED || result.status() == GateStatus.FAILED;
    if (!ran || result.sections().isEmpty()) {
      return ReportText.gateLine(result);
    }
    final String gate = ReportText.displayName(result.name());
    return result.sections().stream()
        .map(section -> sectionLine(gate, section))
        .collect(Collectors.joining(ReportText.NL));
  }

  private static String sectionLine(final String gate, final GateSection section) {
    final String line = (section.errors() > 0 ? "✗ " : "✓ ") + gate + " · " + section.name();
    if (section.errors() > 0) {
      return line + " — " + count(section.errors(), "error");
    }
    return section.warnings() > 0 ? line + " — " + count(section.warnings(), "warning") : line;
  }

  private static String count(final int number, final String noun) {
    return number + " " + noun + (number == 1 ? "" : "s");
  }
}
