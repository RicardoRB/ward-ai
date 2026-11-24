package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Finds the methods that validate a parameter: a method that returns true only for a value of a
 * parameter that has a property, such as a URL whose host is the host of the request. The frontend
 * proves the property in the body and marks each return of the body with a site named {@value
 * #RETURN}, whose qualifier is the parameter and whose first annotation is what the body proves. A
 * mark is {@value #SAFE} when the return gives no value that the check did not prove, and {@value
 * #CALLS} when it gives the result of validators called on a value of the parameter: its arguments
 * pair the span of each such call with the span of the argument. A method that has a return the
 * frontend could not prove marks nothing. The method validates the parameter when it has marks and
 * each {@value #CALLS} mark has a call to a method that validates that argument.
 */
final class ValidatingMethods {
  /** The name of the site that marks a return of a body. */
  static final String RETURN = "<validated-return>";

  private static final String SAFE = "safe";
  private static final String CALLS = "calls";
  private static final int PAIR = 2;

  private ValidatingMethods() {}

  /**
   * What each validated kind of the specification says the method proves, by parameter; {@code
   * previous} holds what the last round found for every method, by key and kind.
   */
  static Map<String, Set<Integer>> of(
      final LocalMethod method,
      final TaintSpec spec,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      Map<String,
      Set<Integer>>> previous
  ) {
    return kinds(spec).stream()
        .map(what -> Map.entry(what, parameters(method, what, methods, known(previous, what))))
        .filter(found -> !found.getValue().isEmpty())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static Map<String, Set<Integer>> known(
      final Map<String,
      Map<String,
      Set<Integer>>> previous,
      final String what
  ) {
    return previous.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getOrDefault(what, Set.of())
            )
        );
  }

  /** The kinds a specification trusts: its validated sanitizers that no method of a file named. */
  private static Set<String> kinds(final TaintSpec spec) {
    return spec.sanitizers().stream()
        .filter(entry -> entry.kind() == FlowSiteKind.VALIDATED)
        .map(TaintEntry::name)
        .filter(name -> name.indexOf(':') < 0)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** The positions of the parameters of the method that it validates for {@code what}. */
  static Set<Integer> parameters(
      final LocalMethod method,
      final String what,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      Set<Integer>> known
  ) {
    final Set<Integer> validated = new TreeSet<>();
    final List<String> parameters = method.parameters();
    if (parameters.size() != method.arity()) {
      return validated;
    }
    for (int index = 0; index < parameters.size(); index++) {
      final List<FlowSite> marks = marks(method.graph(), parameters.get(index), what);
      if (!marks.isEmpty()
          && marks.stream().allMatch(mark -> proved(method.graph(), mark, methods, known))) {
        validated.add(index);
      }
    }
    return validated;
  }

  private static List<FlowSite> marks(
      final ControlFlowGraph graph,
      final String parameter,
      final String what
  ) {
    final List<FlowSite> marks = new ArrayList<>();
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        if (RETURN.equals(site.name())
            && parameter.equals(site.qualifier())
            && site.annotations().contains(what)) {
          marks.add(site);
        }
      }
    }
    return marks;
  }

  private static boolean proved(
      final ControlFlowGraph graph,
      final FlowSite mark,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      Set<Integer>> known
  ) {
    if (mark.annotations().contains(SAFE)) {
      return true;
    }
    for (int pair = 0; pair + 1 < mark.arguments().size(); pair += PAIR) {
      if (validates(
          graph,
          mark.arguments().get(pair),
          mark.arguments().get(pair + 1),
          methods,
          known
      )) {
        return true;
      }
    }
    return false;
  }

  private static boolean validates(
      final ControlFlowGraph graph,
      final Span call,
      final Span argument,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      Set<Integer>> known
  ) {
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowSite site : block.sites()) {
        if (site.kind() == FlowSiteKind.CALL && site.span().equals(call)) {
          final int index = site.arguments().indexOf(argument);
          if (index >= 0 && calledValidator(site, index, methods, known)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean calledValidator(
      final FlowSite site,
      final int index,
      final Map<String,
      LocalMethod> methods,
      final Map<String,
      Set<Integer>> known
  ) {
    return methods.values().stream()
        .anyMatch(
            method ->
                GuardedCalls.calls(method, site)
                    && known.getOrDefault(method.key(), Set.of()).contains(index)
        );
  }
}
