package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds a null that is certain to be dereferenced later although no single dereference sees it as
 * null: a point where a variable holds null, the assignment or the branch of a null test, from
 * which every path dereferences the variable before writing it again, as far as the paths that do
 * not end in an exception go. Each variable that has such a point is reported once, at the first of
 * the places that dereference it.
 */
final class NullDoomed {

  private static final int SEVERAL = 1;

  private final NullSolved body;
  private final Map<Span, Set<NullBackward.Site>> notes = new HashMap<>();
  private final Map<Span, Nullity> atSite = new HashMap<>();

  private NullDoomed(final NullSolved body) {
    this.body = body;
  }

  /** Returns the findings of {@code body}. */
  static List<NullAnalysis.Finding> of(final NullSolved body) {
    final NullDoomed doomed = new NullDoomed(body);
    doomed.collect();
    return doomed.report();
  }

  private static Map<Span, Nullity> copy(final Map<Span, Nullity> state) {
    return new HashMap<>(state);
  }

  private void collect() {
    for (final Map.Entry<Integer, Map<Span, Nullity>> entry : body.thrown().entrySet()) {
      final int id = entry.getKey();
      final Map<Span, Nullity> state = copy(body.normal().getOrDefault(id, entry.getValue()));
      final List<NullEvent> events = body.graph().events().get(id);
      for (int index = 0; index < events.size(); index++) {
        note(state, body.backward().before(id, index));
        remember(events.get(index), state);
        NullStates.apply(state, events.get(index));
      }
      note(state, body.backward().before(id, events.size()));
      edges(body.graph().graph().blocks().get(id), state);
    }
  }

  /** Notes the variables that hold null on an edge and are dereferenced on every path after it. */
  private void edges(final BasicBlock block, final Map<Span, Nullity> state) {
    final NullEvents.Test test = body.graph().tests().get(block.id());
    for (final FlowEdge edge : block.successors()) {
      if (edge.kind() != FlowEdgeKind.EXCEPTION) {
        note(NullStates.refined(state, test, edge.kind()), body.backward().in(edge.target()));
      }
    }
  }

  private void note(final Map<Span, Nullity> state, final NullBackward.Fact fact) {
    if (fact.everything()) {
      return;
    }
    state.forEach(
        (variable, nullity) -> {
          if (nullity == Nullity.NULL && fact.has(variable)) {
            notes
                .computeIfAbsent(variable, key -> new HashSet<>())
                .addAll(fact.sites().get(variable));
          }
        });
  }

  /** Notes what the variables an event dereferences, passes, returns or stores held before it. */
  private void remember(final NullEvent event, final Map<Span, Nullity> state) {
    if (event.kind() == NullEvent.Kind.DEREF) {
      atSite.put(event.node().span(), state.get(event.variable()));
    }
    event.args().forEach(arg -> keep(arg.node().span(), arg.operand(), state));
    if (event.node() != null) {
      keep(event.node().span(), event.operand(), state);
    }
  }

  private void keep(
      final Span at, final NullEvent.Operand operand, final Map<Span, Nullity> state) {
    if (operand.variable() != null) {
      atSite.put(at, state.get(operand.variable()));
    }
  }

  private List<NullAnalysis.Finding> report() {
    final List<NullAnalysis.Finding> found = new ArrayList<>();
    for (final Set<NullBackward.Site> sites : notes.values()) {
      final boolean elsewhere =
          sites.stream().anyMatch(site -> atSite.get(site.at()) != Nullity.NULL);
      if (elsewhere) {
        found.add(finding(sites));
      }
    }
    return found;
  }

  private NullAnalysis.Finding finding(final Set<NullBackward.Site> sites) {
    final NullBackward.Site first =
        sites.stream().min(Comparator.comparingInt(site -> site.at().startOffset())).orElseThrow();
    final boolean exceptional =
        sites.stream().noneMatch(site -> body.normal().containsKey(site.block()));
    return new NullAnalysis.Finding(pattern(sites, first, exceptional), first.at());
  }

  private static NullAnalysis.Pattern pattern(
      final Set<NullBackward.Site> sites,
      final NullBackward.Site first,
      final boolean exceptional) {
    if (sites.size() > SEVERAL) {
      return exceptional
          ? NullAnalysis.Pattern.GUARANTEED_DEREF_ON_EXCEPTION_PATH
          : NullAnalysis.Pattern.GUARANTEED_DEREF;
    }
    return switch (first.use()) {
      case PARAMETER -> NullAnalysis.Pattern.NULL_PARAM_DEREF;
      case RETURN -> NullAnalysis.Pattern.NONNULL_RETURN_VIOLATION;
      case FIELD -> NullAnalysis.Pattern.STORE_INTO_NONNULL_FIELD;
      default ->
          exceptional
              ? NullAnalysis.Pattern.NULL_ON_SOME_PATH_EXCEPTION
              : NullAnalysis.Pattern.NULL_ON_SOME_PATH;
    };
  }
}
