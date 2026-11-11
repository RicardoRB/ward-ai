package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.CrapPorts;
import dev.ward.application.gate.FormatGates;
import dev.ward.application.gate.GateExtensions;
import dev.ward.application.gate.openapi.OpenApiGates;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.infrastructure.finding.FileSystemCoverageFiles;

/** Collects the optional gate dependencies that the quality gate factory injects. */
@Module
interface GateExtensionsModule {
  /** Gathers the OpenAPI gates, the {@code ward} gate's ports and the {@code ward-format} gate. */
  @Provides
  static GateExtensions gateExtensions(
      final OpenApiGates openApi,
      final AnalysisPorts analysis,
      final FormatGates format,
      final CrapPorts crap) {
    return new GateExtensions(openApi, analysis, format, crap);
  }

  /** Gathers what the {@code crap} gate reads: a coverage report reader and the file facts. */
  @Provides
  static CrapPorts crapPorts(final LineCoverageReport reader, final FileSystemCoverageFiles files) {
    return new CrapPorts(reader, files);
  }
}
