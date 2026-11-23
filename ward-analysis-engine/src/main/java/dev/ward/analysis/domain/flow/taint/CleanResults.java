package dev.ward.analysis.domain.flow.taint;

import java.util.List;

/** Finds the methods whose result is clean whatever their callers pass. */
final class CleanResults {
  private CleanResults() {}

  /**
   * Whether a call to the method gives a clean value whatever it is passed: the method returns a
   * value, and no parameter reaches a returned value, for example because the method checks the
   * parameter against an allow-list or strips what makes it unsafe.
   *
   * @param method the method
   * @param seeded the specification with a source for a marked parameter
   */
  static boolean of(final LocalMethod method, final TaintSpec seeded) {
    final List<String> parameters = method.parameters();
    if (parameters.isEmpty()
        || parameters.size() != method.arity()
        || !TaintReturns.returnsValue(method.graph())) {
      return false;
    }
    return parameters.stream()
        .noneMatch(
            parameter ->
                TaintAnalysis.returned(method.withTaintedParameter(parameter), seeded).isPresent()
        );
  }
}
