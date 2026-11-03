package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;
import java.util.Optional;

/** Port that encodes one unit's findings for the analysis result cache and reads them back. */
public interface AnalysisResultCodec {

  /** Encodes the findings of the unit stored under {@code key}. */
  String encode(String key, List<AnalysisFinding> findings);

  /**
   * Decodes the findings stored under {@code key}, or empty when the value is corrupt, from another
   * format version, or stored under a different key.
   */
  Optional<List<AnalysisFinding>> decode(String key, String value);
}
