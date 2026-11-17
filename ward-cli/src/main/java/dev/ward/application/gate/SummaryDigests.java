package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FileTaintSummaries;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The digest of the taint summary of a file, see {@link FileTaintSummaries}: what the unit reads
 * from the files it depends on, which is the same after an edit of one of them that keeps what its
 * callers take from it. The summary is computed from the control-flow graphs, so it is stored under
 * the layout-free digests of the unit and of its dependencies ({@link FlowDigests}) and the rule
 * set; a run in which none of them changed reads it and parses nothing, and an edit of a callee
 * computes the summaries of its dependents again, which costs less than analyzing them.
 */
final class SummaryDigests {
  private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

  private final AnalysisCache cache;
  private final String engineVersion;
  private final FlowDigests flows;
  private final FileTaintSummaries summaries;
  private final Map<String, Optional<String>> known = new HashMap<>();

  SummaryDigests(
      final AnalysisCache cache,
      final String engineVersion,
      final FlowDigests flows,
      final FileTaintSummaries summaries
  ) {
    this.cache = cache;
    this.engineVersion = engineVersion;
    this.flows = flows;
    this.summaries = summaries;
  }

  /** The digests of the graphs of the files, which the summaries are stored under. */
  FlowDigests flows() {
    return flows;
  }

  /**
   * The digest of the summary of {@code unit}, or empty when a rule gives no summary and the unit
   * has to be keyed on the files around it.
   */
  Optional<String> of(final SourceUnit unit, final String ruleSet, final List<SourceUnit> closure) {
    final List<SourceUnit> all = new ArrayList<>(List.of(unit));
    all.addAll(closure);
    final List<String> parts = new ArrayList<>(List.of("taint-summary", engineVersion, ruleSet));
    flows.of(all).forEach(
        related -> {
          parts.add(related.path());
          parts.add(related.digest());
        }
    );
    final String key = AnalysisCacheKey.digest(parts);
    return known.computeIfAbsent(
        key,
        ignored -> cache.read(key).filter(value -> DIGEST.matcher(value).matches())
            .map(Optional::of)
            .orElseGet(() -> compute(key, unit))
    );
  }

  private Optional<String> compute(final String key, final SourceUnit unit) {
    final Optional<String> digest =
        summaries.of(unit).map(text -> AnalysisCacheKey.digest(List.of("summary-text", text)));
    digest.ifPresent(value -> cache.write(key, value));
    return digest;
  }
}
