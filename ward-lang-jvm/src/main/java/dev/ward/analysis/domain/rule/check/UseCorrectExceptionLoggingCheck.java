package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of an Apache Commons Logging method with one {@code Object} parameter, in a catch
 * block, whose only argument is the caught exception, as PMD's UseCorrectExceptionLogging does:
 * only the message of the exception is logged, not its stack trace.
 */
public final class UseCorrectExceptionLoggingCheck extends FactsCheck {

  private static final String LOG = "org.apache.commons.logging.Log";
  private static final String OBJECT = "java.lang.Object";
  private static final String CATCH = "catchClause";
  private static final String EXPRESSION = "expression";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public UseCorrectExceptionLoggingCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(CATCH)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clause -> reportsOf(clause, types).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode clause, final UnitTypeFacts types) {
    final String caught =
        clause.children().stream()
            .filter(child -> MemberDeclarations.IDENTIFIER.equals(child.kind()))
            .map(SyntaxWalk::text)
            .reduce((first, second) -> second)
            .orElse("");
    return SyntaxWalk.visits(clause, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> logsOnly(call, caught, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private static boolean logsOnly(
      final SyntaxNode call, final String caught, final UnitTypeFacts types) {
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    return !caught.isEmpty()
        && arguments.size() == 1
        && caught.equals(SyntaxWalk.text(arguments.get(0)))
        && types
            .callTarget(call.span())
            .filter(UseCorrectExceptionLoggingCheck::isLogCall)
            .isPresent();
  }

  private static boolean isLogCall(final CallTarget target) {
    return LOG.equals(target.declaringType().name())
        && target.parameters().size() == 1
        && OBJECT.equals(target.parameters().get(0).name());
  }
}
