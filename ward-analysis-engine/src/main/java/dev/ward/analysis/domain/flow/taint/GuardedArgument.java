package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An argument that a sink guards, and the spans inside it that hold no taint.
 *
 * @param block the block of the sink
 * @param span the argument
 * @param clean the spans whose sources and reads do not count
 */
record GuardedArgument(BasicBlock block, Span span, List<Span> clean) {
  GuardedArgument {
    clean = List.copyOf(clean);
  }

  /** An argument with no clean part. */
  static GuardedArgument plain(final BasicBlock block, final Span span) {
    return new GuardedArgument(block, span, List.of());
  }

  /**
   * An argument of a sink. When the sink lists the call that is the whole argument, such as {@code
   * JSON.stringify(x)}, and a call that sets a JSON content type on the same response runs before
   * the sink on every path, the arguments of that call are clean. Nothing else may start where the
   * argument starts and run longer, so a call chained on it or a string built around it is not the
   * whole argument.
   */
  static GuardedArgument of(
      final TaintState state,
      final BasicBlock block,
      final FlowSite sink,
      final Span span,
      final TaintEntry entry
  ) {
    if (entry.exceptCalls().isEmpty() || !typedBefore(state.graph(), block, sink)) {
      return plain(block, span);
    }
    final FlowSite longest =
        block.sites().stream()
            .filter(site -> TaintSpans.contains(span, site.span()))
            .filter(site -> site.span().startOffset() == span.startOffset())
            .max(Comparator.comparingInt(site -> site.span().endOffset()))
            .orElse(null);
    return new GuardedArgument(
        block,
        span,
        longest != null && entry.exempts(longest) ? longest.arguments() : List.of()
    );
  }

  /**
   * Whether a call that sets a JSON content type on the response of the sink is made before it on
   * every path: earlier in its block, or in a block that every path to its block passes.
   */
  private static boolean typedBefore(
      final ControlFlowGraph graph,
      final BasicBlock block,
      final FlowSite sink
  ) {
    for (final BasicBlock other : graph.blocks()) {
      for (final FlowSite site : other.sites()) {
        if (site.kind() == FlowSiteKind.CONTENT_TYPE
            && site.qualifier().equals(sink.qualifier())
            && (other.id() == block.id()
                ? site.span().startOffset() < sink.span().startOffset()
                : passes(graph, other.id(), block.id()))) {
          return true;
        }
      }
    }
    return false;
  }

  /** Whether every path from the entry to {@code target} passes {@code through}. */
  private static boolean passes(final ControlFlowGraph graph, final int through, final int target) {
    if (through == graph.entry()) {
      return true;
    }
    final Set<Integer> seen = new HashSet<>();
    final Deque<Integer> work = new ArrayDeque<>();
    work.add(graph.entry());
    seen.add(graph.entry());
    while (!work.isEmpty()) {
      final int current = work.poll();
      if (current == target) {
        return false;
      }
      for (final FlowEdge edge : graph.blocks().get(current).successors()) {
        if (edge.target() != through && seen.add(edge.target())) {
          work.add(edge.target());
        }
      }
    }
    return true;
  }

  /**
   * The taint that reaches the argument: a source written in it, or a tainted read; a proven flow
   * wins over an assumed one.
   */
  Optional<Taint> taint(
      final ReachingDefinitions reaching,
      final Map<AccessSite,
      Taint> origins,
      final TaintSpec spec
  ) {
    final Optional<Taint> direct = directSource(spec);
    if (direct.isPresent() && direct.get().proven()) {
      return direct;
    }
    final Optional<Taint> read = fromReads(reaching, origins, spec);
    return direct.isPresent() && read.isPresent()
        ? Optional.of(Taint.better(direct.get(), read.get()))
        : direct.or(() -> read);
  }

  /** A source call written directly in the argument, such as {@code sink(source())}. */
  private Optional<Taint> directSource(final TaintSpec spec) {
    return block.sites().stream()
        .flatMap(site -> directEntry(site, spec).map(entry -> sourceTaint(site, entry)).stream())
        .reduce(Taint::better);
  }

  private Taint sourceTaint(final FlowSite site, final TaintEntry entry) {
    return new Taint(
        site.name(),
        List.of(new FlowStep(site.span(), "source: " + site.name())),
        entry.assumptions()
    ).assuming(UnfollowedCalls.through(block, span, site.span()));
  }

  private Optional<TaintEntry> directEntry(final FlowSite site, final TaintSpec spec) {
    if (!TaintSpans.contains(span, site.span())
        || isClean(site.span())
        || site.kind() == FlowSiteKind.PARAMETER
        || site.kind() == FlowSiteKind.LITERAL && !site.span().equals(span)
        || TaintSpans.sanitized(block, site.span(), spec)) {
      return Optional.empty();
    }
    return TaintSpans.sourceOf(site, spec);
  }

  /**
   * A read in the argument that sees a tainted write. A read inside a sanitizer's argument is
   * clean, as it is for a write.
   */
  private Optional<Taint> fromReads(
      final ReachingDefinitions reaching,
      final Map<AccessSite,
      Taint> origins,
      final TaintSpec spec
  ) {
    return reaching.uses().stream()
        .filter(use -> taintedRead(use, spec))
        .flatMap(use -> readTaint(reaching, origins, use).stream())
        .reduce(Taint::better);
  }

  private boolean taintedRead(final AccessSite use, final TaintSpec spec) {
    final Span read = use.access().span();
    return use.block() == block.id()
        && use.access().kind() == AccessKind.READ
        && !isClean(read)
        && TaintSpans.contains(span, read)
        && !TaintSpans.sanitized(block, read, spec);
  }

  private Optional<Taint> readTaint(
      final ReachingDefinitions reaching,
      final Map<AccessSite,
      Taint> origins,
      final AccessSite use
  ) {
    final Span read = use.access().span();
    return reaching.reaching(use).stream()
        .map(origins::get)
        .filter(Objects::nonNull)
        .reduce(Taint::better)
        .map(found -> found.assuming(UnfollowedCalls.through(block, span, read)));
  }

  /** Reports whether a span lies inside a clean part. */
  boolean isClean(final Span inner) {
    return clean.stream().anyMatch(part -> TaintSpans.contains(part, inner));
  }
}
