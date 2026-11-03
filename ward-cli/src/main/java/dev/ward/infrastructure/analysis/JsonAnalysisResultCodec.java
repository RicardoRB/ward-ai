package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.application.gate.AnalysisResultCodec;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/**
 * Encodes the analysis result cache entries as Fory JSON. A value that does not read back is a
 * miss.
 */
public final class JsonAnalysisResultCodec implements AnalysisResultCodec {
  static final int FORMAT_VERSION = 1;
  private final JsonCodec codec;
  private final JsonDependencyCodec dependencies;

  /** Creates the codec over {@code codec}. */
  @Inject
  public JsonAnalysisResultCodec(final JsonCodec codec) {
    this.codec = codec;
    this.dependencies = new JsonDependencyCodec(codec);
  }

  @Override
  public String encodeSummary(final String key, final FileSummary summary) {
    return dependencies.encodeSummary(key, summary);
  }

  @Override
  public Optional<FileSummary> decodeSummary(final String key, final String value) {
    return dependencies.decodeSummary(key, value);
  }

  @Override
  public String encodeIndex(final DependencyGraph graph) {
    return dependencies.encodeIndex(graph);
  }

  @Override
  public String encode(final String key, final List<AnalysisFinding> all) {
    final EntryDto entry = new EntryDto();
    entry.formatVersion = FORMAT_VERSION;
    entry.key = key;
    entry.findings = new ArrayList<>();
    for (final AnalysisFinding finding : all) {
      entry.findings.add(FindingDto.from(finding));
    }
    return codec.toJson(entry);
  }

  @Override
  public Optional<List<AnalysisFinding>> decode(final String key, final String value) {
    try {
      final EntryDto entry = codec.fromJson(value, EntryDto.class);
      if (entry == null
          || entry.formatVersion != FORMAT_VERSION
          || entry.findings == null
          || !key.equals(entry.key)) {
        return Optional.empty();
      }
      return Optional.of(findings(entry));
    } catch (final JsonCodecException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static List<AnalysisFinding> findings(final EntryDto entry) {
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final FindingDto finding : entry.findings) {
      if (finding == null) {
        throw new IllegalArgumentException("Cached finding is missing");
      }
      findings.add(finding.toFinding());
    }
    return List.copyOf(findings);
  }

  /** One stored entry; its public fields are serialized. */
  @JsonType
  public static final class EntryDto {
    public int formatVersion;
    public String key;
    public List<FindingDto> findings;
  }

  /** One stored finding; its public fields are serialized. */
  @JsonType
  public static final class FindingDto {
    public String ruleId;
    public String severity;
    public String message;
    public String file;
    public Integer line;
    public Integer column;
    public String helpUri;

    static FindingDto from(final AnalysisFinding finding) {
      final FindingDto dto = new FindingDto();
      dto.ruleId = finding.ruleId();
      dto.severity = finding.severity().name();
      dto.message = finding.message();
      dto.file = finding.file();
      dto.line = finding.line();
      dto.column = finding.column();
      dto.helpUri = finding.helpUri();
      return dto;
    }

    AnalysisFinding toFinding() {
      if (ruleId == null || severity == null || message == null) {
        throw new IllegalArgumentException("Cached finding is incomplete");
      }
      return new AnalysisFinding(
          ruleId, AnalysisSeverity.valueOf(severity), message, file, line, column, helpUri);
    }
  }
}
