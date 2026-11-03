package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import java.util.Optional;

/**
 * Keeps the file summaries in the analysis cache. A stored summary is used only when it decodes and
 * names the unit's path, so a corrupt entry is summarized again.
 */
final class AnalysisSummaryCache implements FileSummaryCache {
  private final AnalysisCache cache;
  private final AnalysisResultCodec codec;
  private final String engineVersion;

  /** Creates the summary cache over {@code cache}. */
  AnalysisSummaryCache(
      final AnalysisCache cache, final AnalysisResultCodec codec, final String engineVersion) {
    this.cache = cache;
    this.codec = codec;
    this.engineVersion = engineVersion;
  }

  @Override
  public Optional<FileSummary> read(final SourceUnit unit) {
    final String key = AnalysisCacheKey.summary(engineVersion, unit);
    return cache
        .read(key)
        .flatMap(value -> codec.decodeSummary(key, value))
        .filter(summary -> summary.path().equals(unit.path()));
  }

  @Override
  public void write(final SourceUnit unit, final FileSummary summary) {
    final String key = AnalysisCacheKey.summary(engineVersion, unit);
    cache.write(key, codec.encodeSummary(key, summary));
  }
}
