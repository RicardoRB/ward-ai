package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.flow.taint.MemberVouching;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RelatedFile;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Gives a rule that follows calls into other files the graphs of the files its unit depends on,
 * directly or through others. A file is parsed and lowered once per run. A file that does not parse
 * or lower has no graphs, and no error is recorded for it, as it is not the file being checked. Safe
 * for the threads of one run: the graphs are kept by path, which names one unit of the snapshot.
 */
final class RelatedFlows {
  private final Map<AnalysisLanguage, FrontendSupport> support;
  private final DependencySnapshot snapshot;
  private final Map<String, Lowered> lowered = new ConcurrentHashMap<>();

  RelatedFlows(
      final Map<AnalysisLanguage,
      FrontendSupport> support,
      final DependencySnapshot snapshot
  ) {
    this.support = support;
    this.snapshot = snapshot;
  }

  /** Returns {@code own}, with the graphs of the related files when the rule reads their bodies. */
  FlowView with(final CompiledRule rule, final SourceUnit unit, final FlowView own) {
    if (!rule.definition().capability().readsDependencyBodies()) {
      return own;
    }
    final List<RelatedFile> files =
        snapshot.closureOf(unit.path()).stream()
            .map(this::loweredOf)
            .flatMap(done -> done.file().stream())
            .toList();
    return new FlowView(own.graphs(), relatedOf(unit, own.graphs()), files);
  }

  /**
   * The graphs of the files the unit depends on. When the unit or one of them has a null check
   * that a membership may vouch for, also the graphs of the files that depend on them: the proof
   * reads every place that can write to the collection.
   */
  List<ControlFlowGraph> relatedOf(final SourceUnit unit, final List<ControlFlowGraph> own) {
    final List<ControlFlowGraph> related =
        snapshot.closureOf(unit.path()).stream()
            .flatMap(other -> graphsOf(other).stream())
            .toList();
    if (!MemberVouching.mayVouch(own) && !MemberVouching.mayVouch(related)) {
      return related;
    }
    return Stream.concat(
        related.stream(),
        snapshot.dependentsAround(unit.path()).stream()
            .flatMap(other -> graphsOf(other).stream())
    ).toList();
  }

  /** The graphs of a file of the run, lowered once. Lowers outside the map, so no thread waits for another file; a second result is dropped. */
  List<ControlFlowGraph> graphsOf(final SourceUnit unit) {
    return loweredOf(unit).graphs();
  }

  private Lowered loweredOf(final SourceUnit unit) {
    final Lowered known = lowered.get(unit.path());
    if (known != null) {
      return known;
    }
    final Lowered fresh = lower(unit);
    final Lowered first = lowered.putIfAbsent(unit.path(), fresh);
    return first == null ? fresh : first;
  }

  private Lowered lower(final SourceUnit unit) {
    final FrontendSupport language = support.get(unit.language());
    if (language == null) {
      return Lowered.NONE;
    }
    try {
      final ParseResult parsed = language.frontend().parse(unit);
      if (!parsed.diagnostics().isEmpty()) {
        return Lowered.NONE;
      }
      return new Lowered(
          language.lowering().lower(unit, parsed.tree()).graphs(),
          Optional.of(new RelatedFile(unit, parsed.tree()))
      );
    } catch (final AnalysisFailedException exception) {
      return Lowered.NONE;
    }
  }

  /** The graphs of a file and, when it parsed and lowered, its tree. */
  private record Lowered(List<ControlFlowGraph> graphs, Optional<RelatedFile> file) {
    static final Lowered NONE = new Lowered(List.of(), Optional.empty());
  }
}
