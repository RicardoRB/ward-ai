package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The digest of what a file gives to the taint analysis of the files that depend on it: its
 * layout-free control-flow graphs, see {@link GraphDigest}. A comment edit, a blank line or a
 * reformat of a callee leaves its digest as it was, so the cached results of its dependents stay
 * valid, while a change of a name, a call, an argument or a statement changes it. A digest is
 * stored under the file's text, so a warm run reads it and parses nothing. A file that cannot be
 * parsed or lowered has no graphs, as for the analysis, and so one digest for all such files.
 *
 * <p>The digest keys the stored taint summary of {@link SummaryDigests}; a unit is keyed on it
 * directly only when a rule gives no summary.
 */
final class FlowDigests {
  private static final Logger LOG = Logger.getLogger(FlowDigests.class.getName());
  private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

  private final AnalysisCache cache;
  private final String engineVersion;
  private final Map<AnalysisLanguage, FrontendSupport> support =
      new EnumMap<>(AnalysisLanguage.class);
  private final Map<String, String> known = new ConcurrentHashMap<>();

  FlowDigests(
      final AnalysisCache cache,
      final String engineVersion,
      final List<FrontendSupport> frontends
  ) {
    this.cache = cache;
    this.engineVersion = engineVersion;
    frontends.forEach(frontend -> support.put(frontend.language(), frontend));
  }

  /** The path and digest of each unit, in the order given. */
  List<Related> of(final List<SourceUnit> units) {
    return units.stream().map(unit -> new Related(unit.path(), of(unit))).toList();
  }

  private String of(final SourceUnit unit) {
    final String key =
        AnalysisCacheKey.digest(List.of("flow-digest", engineVersion, unit.path(), unit.text()));
    final String digest = known.get(key);
    if (digest != null) {
      return digest;
    }
    final String fresh = stored(key).orElseGet(() -> compute(key, unit));
    final String first = known.putIfAbsent(key, fresh);
    return first == null ? fresh : first;
  }

  private Optional<String> stored(final String key) {
    return cache.read(key).filter(value -> DIGEST.matcher(value).matches());
  }

  private String compute(final String key, final SourceUnit unit) {
    final String digest = AnalysisCacheKey.digest(List.of("graphs", GraphDigest.of(graphs(unit))));
    cache.write(key, digest);
    return digest;
  }

  private List<ControlFlowGraph> graphs(final SourceUnit unit) {
    final FrontendSupport language = support.get(unit.language());
    if (language == null) {
      return List.of();
    }
    try {
      final ParseResult parsed = language.frontend().parse(unit);
      return parsed.diagnostics().isEmpty()
          ? language.lowering().lower(unit, parsed.tree()).graphs()
          : List.of();
    } catch (final AnalysisFailedException exception) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.log(Level.FINE, "no flow digest graphs for a file that cannot be analyzed");
      }
      return List.of();
    }
  }

  /** One related file: its path and the digest of its graphs. */
  record Related(String path, String digest) {}
}
