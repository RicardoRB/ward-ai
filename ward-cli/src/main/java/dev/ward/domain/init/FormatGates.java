package dev.ward.domain.init;

import dev.ward.domain.gate.SupportedQualityGates;

/** The gate that runs Ward's native formatter; it replaces every external formatter. */
public final class FormatGates {
  /** The gate that runs the native formatter. */
  public static final String WARD_FORMAT = SupportedQualityGates.FORMAT;

  private FormatGates() {}
}
