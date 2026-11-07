package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a debug or trace call on a logger whose message is built by string concatenation outside
 * any {@code if}, which builds the message even when the level is off. Guarding is approximated by
 * an enclosing {@code if}, since the tree does not read the guard's condition.
 */
public final class GuardLogStatementCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String ARGUMENTS = "arguments";
  private static final String IF = "'if'";
  private static final Set<String> LOGGERS =
      Set.of("org.slf4j.Logger", "org.apache.logging.log4j.Logger", "java.util.logging.Logger");
  private static final Set<String> LEVELS = Set.of("debug", "trace");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public GuardLogStatementCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public FactNeed needs() {
    return TypedChecks.CALLS;
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final dev.ward.analysis.domain.rule.FlowView flow,
      final dev.ward.analysis.domain.rule.ScopeView scopes,
      final UnitTypeFacts types,
      final dev.ward.analysis.domain.ir.SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final SyntaxNode call = visit.node();
      if (isUnguardedConcatenation(call, visit.ancestors(), types)) {
        matches.add(CheckSupport.at(call.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isUnguardedConcatenation(
      final SyntaxNode call, final List<SyntaxNode> ancestors, final UnitTypeFacts types) {
    final boolean logsDebug =
        TypedChecks.calledName(call, types).filter(LEVELS::contains).isPresent()
            && TypedChecks.declaringType(call, types).filter(LOGGERS::contains).isPresent();
    return logsDebug
        && concatenates(call)
        && ancestors.stream().noneMatch(GuardLogStatementCheck::hasIf);
  }

  private static boolean concatenates(final SyntaxNode call) {
    return SyntaxWalk.descendants(call).stream()
        .filter(node -> ARGUMENTS.equals(node.kind()))
        .anyMatch(args -> SyntaxWalk.text(args).contains("+"));
  }

  private static boolean hasIf(final SyntaxNode node) {
    return node.children().stream().anyMatch(child -> IF.equals(child.kind()));
  }
}
