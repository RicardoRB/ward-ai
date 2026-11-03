package dev.ward.analysis.infrastructure.node;

import dev.ward.analysis.domain.node.ComplexityRule;
import dev.ward.analysis.domain.node.FileLengthRule;
import dev.ward.analysis.domain.node.FunctionLengthRule;
import dev.ward.analysis.domain.node.NamingRule;
import dev.ward.analysis.domain.node.NestingDepthRule;
import dev.ward.analysis.domain.node.ParameterCountRule;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;

/**
 * Builds the structural rules of TypeScript and JavaScript from their rule definitions. The id
 * names the metric after its slash; the body holds the limit, as {@link NodeRuleBody} reads it.
 */
public final class NodeRuleMatcher implements RuleMatcher {

  private static final String FUNCTION_LENGTH = "function-length";
  private static final String PARAMETER_COUNT = "parameter-count";
  private static final String COMPLEXITY = "complexity";
  private static final String FILE_LENGTH = "file-length";
  private static final String NAMING = "naming";
  private static final String NESTING_DEPTH = "nesting-depth";

  private static final int FUNCTION_LENGTH_LIMIT = 50;
  private static final int PARAMETER_COUNT_LIMIT = 3;
  private static final int COMPLEXITY_LIMIT = 20;
  private static final int FILE_LENGTH_LIMIT = 300;
  private static final int NESTING_DEPTH_LIMIT = 4;

  /** Creates the matcher. It holds no state, so one instance serves every gate run. */
  public NodeRuleMatcher() {}

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    final String metric = definition.id().substring(definition.id().indexOf('/') + 1);
    return switch (metric) {
      case FUNCTION_LENGTH ->
          new FunctionLengthRule(definition, NodeRuleBody.limit(definition, FUNCTION_LENGTH_LIMIT));
      case PARAMETER_COUNT ->
          new ParameterCountRule(definition, NodeRuleBody.limit(definition, PARAMETER_COUNT_LIMIT));
      case COMPLEXITY ->
          new ComplexityRule(definition, NodeRuleBody.limit(definition, COMPLEXITY_LIMIT));
      case FILE_LENGTH ->
          new FileLengthRule(definition, NodeRuleBody.limit(definition, FILE_LENGTH_LIMIT));
      case NAMING -> naming(definition);
      case NESTING_DEPTH ->
          new NestingDepthRule(definition, NodeRuleBody.limit(definition, NESTING_DEPTH_LIMIT));
      default -> throw new AnalysisFailedException("no structural rule is named " + metric);
    };
  }

  private static CompiledRule naming(final RuleDefinition definition) {
    NodeRuleBody.requireEmpty(definition);
    return new NamingRule(definition);
  }
}
