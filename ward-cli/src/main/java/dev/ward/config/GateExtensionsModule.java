package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.FormatGates;
import dev.ward.application.gate.GateExtensions;
import dev.ward.application.gate.SecretGates;
import dev.ward.application.gate.openapi.OpenApiGates;

/** Collects the optional gate dependencies that the quality gate factory injects. */
@Module
interface GateExtensionsModule {
  /**
   * Gathers the OpenAPI gates, the {@code ward} gate's ports and the {@code ward-format} and {@code
   * secrets} gates.
   */
  @Provides
  static GateExtensions gateExtensions(
      final OpenApiGates openApi,
      final AnalysisPorts analysis,
      final FormatGates format,
      final SecretGates secrets) {
    return new GateExtensions(openApi, analysis, format, secrets);
  }
}
