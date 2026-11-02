package dev.ward.application.gate;

import dev.ward.application.gate.openapi.OpenApiGates;

/**
 * The optional gate dependencies that {@code QualityGateFactory} injects beside its core ports.
 *
 * @param openApi builds the OpenAPI gates, or {@code null} when they are unavailable
 * @param analysis gives the {@code ward} gate its rules and sources, or {@code null} when it is
 *     unavailable
 */
public record GateExtensions(OpenApiGates openApi, AnalysisPorts analysis) {

  /** No optional dependencies: every extension gate is reported as unavailable. */
  public static final GateExtensions NONE = new GateExtensions(null, null);
}
