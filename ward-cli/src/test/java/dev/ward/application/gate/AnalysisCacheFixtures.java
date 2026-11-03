package dev.ward.application.gate;

import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.infrastructure.analysis.JsonAnalysisResultCodec;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.util.Optional;

/** Shared doubles for the analysis result cache in tests. */
final class AnalysisCacheFixtures {
  /** A cache that stores nothing, as a run sees it when no project cache is bound. */
  static final AnalysisCache NO_CACHE =
      new AnalysisCache() {
        @Override
        public Optional<String> read(final String key) {
          return Optional.empty();
        }

        @Override
        public void write(final String key, final String value) {}
      };

  private AnalysisCacheFixtures() {}

  /** The codec the gate uses in production, over Fory JSON. */
  static AnalysisResultCodec codec() {
    return new JsonAnalysisResultCodec(new ForyJsonCodec());
  }
}
