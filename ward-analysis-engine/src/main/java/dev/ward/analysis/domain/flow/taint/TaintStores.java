package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Taints a container that a tainted value is stored into, so an array, a holder object, a list or a
 * map that received tainted data is tainted. A store is an assignment to a member or an element of
 * a variable ({@code box.f = v}, {@code a[i] = v}), or a call that a propagator entry names, which
 * stores the listed arguments into its receiver ({@code list.add(v)}). The definitions of the
 * variable that reach the store become tainted, so every later read of the container, in this
 * function or in a callee that receives it, sees the taint.
 *
 * <p>The model is coarse on purpose: one tainted element taints the whole container, the order of
 * stores and reads is ignored, and a later store of a clean value does not clean it. Only a
 * variable named by itself is a container; {@code this.items} and {@code a.b.c} are not followed.
 */
final class TaintStores {

  private static final String STORED = "stored into ";
  private static final Pattern VARIABLE = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final String THIS = "this";

  private TaintStores() {}

  /**
   * Taints the definitions that reach the container of each store of a tainted value.
   *
   * @return whether a definition gained taint
   */
  static boolean apply(
      final ControlFlowGraph graph,
      final ReachingDefinitions reaching,
      final TaintSpec spec,
      final Map<AccessSite, Taint> tainted) {
    boolean changed = false;
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        changed |= store(block, site, reaching, spec, tainted);
      }
    }
    return changed;
  }

  private static boolean store(
      final BasicBlock block,
      final FlowSite site,
      final ReachingDefinitions reaching,
      final TaintSpec spec,
      final Map<AccessSite, Taint> tainted) {
    if (!isContainer(site.qualifier())) {
      return false;
    }
    for (final Span value : stored(site, spec)) {
      final Optional<Taint> taint = TaintAnalysis.taintOf(block, value, reaching, tainted, spec);
      if (taint.isPresent()) {
        return taintContainer(block, site, taint.get(), reaching, tainted);
      }
    }
    return false;
  }

  private static boolean isContainer(final String qualifier) {
    return VARIABLE.matcher(qualifier).matches() && !THIS.equals(qualifier);
  }

  /** The spans of the values that the site stores: an assigned value, or a propagator's inputs. */
  private static List<Span> stored(final FlowSite site, final TaintSpec spec) {
    if (site.kind() == FlowSiteKind.MEMBER_WRITE) {
      return site.arguments();
    }
    if (site.kind() != FlowSiteKind.CALL) {
      return List.of();
    }
    final List<Span> values = new ArrayList<>();
    spec.propagators().stream()
        .filter(entry -> entry.matches(site))
        .flatMap(entry -> entry.arguments().stream())
        .filter(index -> index < site.arguments().size())
        .forEach(index -> values.add(site.arguments().get(index)));
    return values;
  }

  private static boolean taintContainer(
      final BasicBlock block,
      final FlowSite site,
      final Taint taint,
      final ReachingDefinitions reaching,
      final Map<AccessSite, Taint> tainted) {
    boolean changed = false;
    for (final AccessSite use : containerReads(block, site, reaching)) {
      for (final AccessSite definition : reaching.reaching(use)) {
        changed |= taint(definition, site, taint, tainted);
      }
    }
    return changed;
  }

  /**
   * The reads of the container variable that a store uses. An assignment holds its target; a call's
   * span starts after its receiver, so its container is the nearest read of the name before it in
   * the same statement.
   */
  private static List<AccessSite> containerReads(
      final BasicBlock block, final FlowSite site, final ReachingDefinitions reaching) {
    final Optional<SyntaxNode> statement = TaintSpans.nodeContaining(block, site.span());
    final List<AccessSite> reads =
        reaching.uses().stream()
            .filter(use -> use.block() == block.id())
            .filter(use -> use.access().kind() == AccessKind.READ)
            .filter(use -> use.access().name().equals(site.qualifier()))
            .filter(use -> statement.isPresent())
            .filter(use -> TaintSpans.contains(statement.get().span(), use.access().span()))
            .toList();
    if (site.kind() == FlowSiteKind.MEMBER_WRITE) {
      return reads.stream()
          .filter(use -> TaintSpans.contains(site.span(), use.access().span()))
          .toList();
    }
    return reads.stream()
        .filter(use -> use.access().span().endOffset() <= site.span().startOffset())
        .reduce((earlier, later) -> later)
        .stream()
        .toList();
  }

  private static boolean taint(
      final AccessSite definition,
      final FlowSite site,
      final Taint taint,
      final Map<AccessSite, Taint> tainted) {
    if (tainted.containsKey(definition)) {
      return false;
    }
    final List<FlowStep> path = new ArrayList<>(taint.path());
    path.add(new FlowStep(site.span(), STORED + site.qualifier()));
    tainted.put(definition, new Taint(taint.source(), path));
    return true;
  }
}
