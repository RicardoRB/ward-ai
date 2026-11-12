package dev.ward.infrastructure.cli.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.SupportedQualityGates;

/** What follows a gate's name on its line of the text report. */
final class GateLineSuffix {
  private static final String DASH = " — ";

  private GateLineSuffix() {}

  static String of(final GateResult result) {
    return switch (result.status()) {
      case UNAVAILABLE -> DASH + "unavailable";
      case EXECUTION_ERROR -> DASH + "error";
      case SKIPPED -> result.summary() == null ? "" : DASH + result.summary();
      default -> finished(result);
    };
  }

  /** The vulnerabilities check names its databases and their dates in every finished line. */
  private static String finished(final GateResult result) {
    if (SupportedQualityGates.VULNERABILITIES.equals(result.type()) && result.summary() != null) {
      return DASH + result.summary();
    }
    return result.status() == GateStatus.FAILED && result.errors() != null
        ? DASH + result.errors() + " errors"
        : "";
  }
}
