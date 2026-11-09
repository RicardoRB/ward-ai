package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports the assignment of the result of a module function that returns no value, like Pylint
 * {@code assignment-from-no-return}, or only {@code None}, like {@code assignment-from-none}.
 * Generators, functions that end in a raise without a return, and async or decorated functions are
 * not reported.
 */
public final class AssignedCallCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** The function has no return statement at all. */
    NO_RETURN,
    /** Every return statement of the function returns {@code None}. */
    NONE
  }

  private static final Set<String> STATEMENTS = Set.of("expr_stmt");
  private static final Set<String> YIELDS = Set.of("yield_expr", "yield_stmt");
  private static final Set<String> RETURNS = Set.of("return_stmt");
  private static final String RETURN_TEXT = "return";
  private static final String RETURN_NONE_TEXT = "returnNone";
  private static final String RAISE = "raise";
  private static final Pattern ASSIGNMENT = Pattern.compile("^([\\w.,\\[\\]]+)=(.+)$");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public AssignedCallCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, STATEMENTS)) {
      final Matcher assignment = ASSIGNMENT.matcher(SyntaxWalk.text(visit.node()));
      if (assignment.matches() && reports(callee(definitions, assignment.group(2)))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean reports(final Optional<SyntaxNode> function) {
    return function.flatMap(AssignedCallCheck::outcome).filter(problem::equals).isPresent();
  }

  /** The module function a plain call of the text invokes, if it is one. */
  private static Optional<SyntaxNode> callee(
      final Map<String, SyntaxNode> definitions, final String value) {
    return PlainCall.of(value)
        .map(call -> definitions.get(call.callee()))
        .filter(definition -> SyntaxKinds.FUNCDEF.equals(definition.kind()));
  }

  /** What the function's returns make of an assignment of its result, if anything. */
  static Optional<Problem> outcome(final SyntaxNode function) {
    if (!FunctionBodies.ownScope(function, YIELDS).isEmpty()
        || SyntaxWalk.text(function).startsWith("async")) {
      return Optional.empty();
    }
    final List<SyntaxNode> returns = FunctionBodies.ownScope(function, RETURNS);
    if (returns.isEmpty()) {
      return endsWithRaise(function) ? Optional.empty() : Optional.of(Problem.NO_RETURN);
    }
    final boolean onlyNone = returns.stream().allMatch(AssignedCallCheck::returnsNone);
    return onlyNone ? Optional.of(Problem.NONE) : Optional.empty();
  }

  private static boolean returnsNone(final SyntaxNode returned) {
    final String text = SyntaxWalk.text(returned);
    return RETURN_TEXT.equals(text) || RETURN_NONE_TEXT.equals(text);
  }

  private static boolean endsWithRaise(final SyntaxNode function) {
    final SyntaxNode body = FunctionBodies.child(function, "block").orElse(function);
    final List<SyntaxNode> statements = new ArrayList<>();
    for (final SyntaxNode child : body.children()) {
      if (!SyntaxWalk.text(child).isBlank()) {
        statements.add(child);
      }
    }
    return !statements.isEmpty()
        && SyntaxWalk.text(statements.get(statements.size() - 1)).strip().startsWith(RAISE);
  }
}
