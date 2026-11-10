package dev.ward.application.gate;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.List;
import java.util.Optional;

/**
 * Port that encodes the entries of the analysis cache, the unit results, file summaries and the
 * reverse-dependency index, and reads them back.
 */
public interface AnalysisResultCodec {

  /** Encodes the findings of the unit stored under {@code key}. */
  String encode(String key, List<AnalysisFinding> findings);

  /**
   * Decodes the findings stored under {@code key}, or empty when the value is corrupt, from another
   * format version, or stored under a different key.
   */
  Optional<List<AnalysisFinding>> decode(String key, String value);

  /** Encodes the summary of the unit stored under {@code key}. */
  String encodeSummary(String key, FileSummary summary);

  /**
   * Decodes the summary stored under {@code key}, or empty when the value is corrupt, from another
   * format version, or stored under a different key.
   */
  Optional<FileSummary> decodeSummary(String key, String value);

  /** Encodes the reverse-dependency index of the graph: the dependents of each depended-on file. */
  String encodeIndex(DependencyGraph graph);
}
