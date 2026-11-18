package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.UnitViews;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Runs the prepared per-file rules of one engine run over its units, rule by rule or file by file.
 * Both orders record the same findings and errors. Safe for the threads of one run: nothing here
 * changes during the run except the graphs that {@link RelatedFlows} gathers, which are safe to
 * share, and each call works on trees and a collector of its own. The type-fact sources keep
 * mutable models of the project, so they are asked under one lock, one unit at a time.
 */
final class UnitRuns {
  private final Map<AnalysisLanguage, FrontendSupport> support;
  private final List<PreparedRule> prepared;
  private final Map<AnalysisLanguage, List<FactNeed>> needs;
  private final Set<String> nodeKinds;
  private final DependencySnapshot snapshot;
  private final RelatedFlows related;

  UnitRuns(
      final Map<AnalysisLanguage,
      FrontendSupport> support,
      final List<PreparedRule> prepared,
      final DependencySnapshot snapshot
  ) {
    final Lock typeSources = new ReentrantLock();
    this.support =
        support.values().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    FrontendSupport::language,
                    frontend -> withLockedTypes(frontend, typeSources)
                )
            );
    this.prepared = List.copyOf(prepared);
    this.needs = Map.copyOf(PreparedRule.needs(prepared));
    this.nodeKinds = PreparedRule.nodeKinds(prepared);
    this.snapshot = snapshot;
    this.related = new RelatedFlows(support, snapshot);
  }

  /** The trees and the collector that one call works on. */
  private record Work(UnitTrees trees, SharedNodes nodes, RuleRunCollector collector) {}

  /** Runs each rule on every unit, in rule order, over one set of trees and one collector. */
  RuleRunCollector ruleByRule(final List<SourceUnit> units) {
    final Work work = work();
    for (final PreparedRule rule : prepared) {
      units.stream()
          .filter(unit -> rule.runnable().contains(unit.language()))
          .forEach(unit -> rule.rule().ifPresent(compiled -> run(compiled, unit, work)));
    }
    return work.collector();
  }

  /**
   * Runs every rule on {@code unit}, in rule order, with trees and a collector of its own, so the
   * trees of a unit are dropped when its rules are done.
   */
  RuleRunCollector fileByFile(final SourceUnit unit) {
    final Work work = work();
    prepared.stream()
        .filter(rule -> rule.runnable().contains(unit.language()))
        .flatMap(rule -> rule.rule().stream())
        .forEach(compiled -> run(compiled, unit, work));
    return work.collector();
  }

  private Work work() {
    return new Work(
        new UnitTrees(support, needs),
        new SharedNodes(nodeKinds),
        new RuleRunCollector()
    );
  }

  private void run(final CompiledRule rule, final SourceUnit unit, final Work work) {
    final RuleRunCollector collector = work.collector();
    final UnitTrees trees = work.trees();
    final Optional<SyntaxNode> root = trees.parse(unit, collector);
    if (root.isEmpty()) {
      return;
    }
    try {
      final DependencyView dependencies = view(rule, unit);
      final UnitViews views =
          new UnitViews(
              related.with(rule, unit, trees.flows(rule, unit, root.get(), collector)),
              trees.scopes(rule, unit, root.get(), dependencies, collector),
              trees.types(rule, unit, root.get(), collector),
              work.nodes().of(rule, unit, root.get())
          );
      rule.matches(root.get(), dependencies, views, unit)
          .forEach(match -> collector.finding(RuleFindings.of(rule.definition(), unit, match)));
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(rule.definition().id(), unit.path(), "the rule could not run")
      );
    } catch (final StackOverflowError overflow) {
      // A pattern that recurses on a very long text fails this rule on this unit, not the run.
      collector.recordError(
          new RuleExecutionError(
              rule.definition().id(),
              unit.path(),
              "the rule ran out of stack on this file"
          )
      );
    }
  }

  /** Returns {@code frontend} with a type-fact source that answers only while it holds the lock. */
  private static FrontendSupport withLockedTypes(final FrontendSupport frontend, final Lock lock) {
    return new FrontendSupport(
        frontend.language(),
        frontend.frontend(),
        frontend.capabilities(),
        frontend.lowering(),
        frontend.scopes(),
        frontend.types().map(facts -> locked(facts, lock)),
        frontend.manifests(),
        frontend.projectLanguages()
    );
  }

  private static TypeFacts locked(final TypeFacts facts, final Lock lock) {
    return (unit, spans, kinds) -> {
      lock.lock();
      try {
        return facts.factsOf(unit, spans, kinds);
      } finally {
        lock.unlock();
      }
    };
  }

  /** Only a rule that reads {@code SYMBOLS} or {@code SCOPES} sees the files around the unit. */
  private DependencyView view(final CompiledRule rule, final SourceUnit unit) {
    if (!rule.definition().capability().readsDependencies()) {
      return DependencyView.NONE;
    }
    return snapshot.view(unit.path());
  }
}
