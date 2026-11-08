package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the null dereferences and null uses of one Java file with a data-flow over the control-flow
 * graph of each of its bodies, the way SpotBugs' {@code NP} and {@code RCN} patterns do: a variable
 * that is null where it is dereferenced, a parameter marked possibly null that is dereferenced
 * unchecked, a null passed, returned or stored where it must not be, a null check of a value
 * already dereferenced, and a null that is dereferenced on every path after it.
 */
final class NullAnalysis {

  /** The patterns the analysis reports. */
  enum Pattern {
    /** A variable that is null on every path is dereferenced. */
    ALWAYS_NULL,
    /** A variable that is null on an exception path is dereferenced. */
    ALWAYS_NULL_EXCEPTION,
    /** {@code close()} is called on a variable that is null. */
    CLOSING_NULL,
    /** A parameter marked possibly null is dereferenced without a check. */
    ARGUMENT_MIGHT_BE_NULL,
    /** Null is passed for a parameter that is annotated never to be null. */
    NONNULL_PARAM_VIOLATION,
    /** A null that is certain to reach a parameter that must not be null. */
    NULL_PARAM_DEREF,
    /** Null is passed to a parameter that a non-virtual callee dereferences. */
    NULL_PARAM_DEREF_NONVIRTUAL,
    /** Null is passed to a parameter that every possible callee dereferences. */
    NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS,
    /** Null is stored into a field annotated never to be null. */
    STORE_INTO_NONNULL_FIELD,
    /** Null is returned from a method annotated never to return null. */
    NONNULL_RETURN_VIOLATION,
    /** A null on some path is certain to be dereferenced at one place. */
    NULL_ON_SOME_PATH,
    /** The same, in code that only an exception reaches. */
    NULL_ON_SOME_PATH_EXCEPTION,
    /** A null on some path is certain to be dereferenced, at one of several places. */
    GUARANTEED_DEREF,
    /** The same, in code that only an exception reaches. */
    GUARANTEED_DEREF_ON_EXCEPTION_PATH,
    /** A value is compared with null after it was dereferenced. */
    WOULD_HAVE_BEEN_NPE
  }

  /**
   * One finding.
   *
   * @param pattern what was found
   * @param at where
   */
  record Finding(Pattern pattern, Span at) {}

  private NullAnalysis() {}

  /** Analyses {@code root}, the syntax of {@code unit}. */
  static List<Finding> of(final SyntaxNode root, final SourceUnit unit, final UnitTypeFacts types) {
    final NullScope scope = NullScope.of(root);
    final List<NullSolved> solved = new ArrayList<>();
    for (final ControlFlowGraph body : new JavaControlFlow().lower(unit, root).graphs()) {
      solved.add(NullSolved.of(body, scope, types));
    }
    final Map<String, Set<Integer>> callees = callees(solved);
    final NullReport report = new NullReport(types, scope, callees);
    final List<Finding> found = new ArrayList<>();
    for (final NullSolved body : solved) {
      found.addAll(report.direct(body));
      found.addAll(NullDoomed.of(body));
    }
    final Set<Span> caught = NullCatches.of(root);
    return found.stream().filter(finding -> !within(caught, finding.at())).toList();
  }

  private static boolean within(final Set<Span> caught, final Span at) {
    return caught.stream()
        .anyMatch(
            span -> at.startOffset() >= span.startOffset() && at.endOffset() <= span.endOffset());
  }

  /** Returns the parameters that each method dereferences on every path, by method key. */
  private static Map<String, Set<Integer>> callees(final List<NullSolved> solved) {
    final Map<String, Set<Integer>> out = new HashMap<>();
    for (final NullSolved body : solved) {
      final String name = body.graph().graph().body();
      if (body.method() != null) {
        out.put(body.type() + name.substring(name.indexOf('#')), derefs(body));
      }
    }
    return out;
  }

  private static Set<Integer> derefs(final NullSolved body) {
    final NullBackward.Fact fact = body.backward().in(body.graph().graph().entry());
    final List<dev.ward.analysis.domain.ir.VariableAccess> parameters =
        NullSolved.parameters(body.graph().graph());
    final Set<Integer> out = new java.util.HashSet<>();
    for (int index = 0; index < parameters.size(); index++) {
      if (!fact.everything() && fact.has(parameters.get(index).declaration())) {
        out.add(index);
      }
    }
    return out;
  }
}
