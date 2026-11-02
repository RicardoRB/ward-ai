package dev.ward.analysis.domain.rule;

/**
 * Port that turns a declarative rule body into a runnable {@link CompiledRule}. Adapters own the
 * body format, so the engine does not depend on it.
 */
@FunctionalInterface
public interface RuleMatcher {

  /**
   * Builds the rule that {@code definition} describes.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the body is malformed
   */
  CompiledRule compile(RuleDefinition definition);
}
