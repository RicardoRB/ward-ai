package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports assignments to the variable of a loop inside its body. A classic {@code for} variable is
 * checked when {@code forReassign} is {@code deny}. An enhanced {@code for} variable is checked
 * when {@code foreachReassign} is {@code deny}, or, for {@code firstOnly}, everywhere except the
 * first statement of the body.
 */
public final class AvoidReassigningLoopVariablesCheck implements UnitCheck {

  private static final String DENY = "deny";
  private static final String FIRST_ONLY = "firstOnly";
  private static final String STATEMENT = "statement";

  private final String forReassign;
  private final String foreachReassign;
  private final String message;

  /**
   * Creates the check.
   *
   * @param forReassign {@code allow} or {@code deny} for classic {@code for} variables
   * @param foreachReassign {@code allow}, {@code firstOnly} or {@code deny} for enhanced variables
   * @param message the message; {@code {value}} is the variable
   */
  public AvoidReassigningLoopVariablesCheck(
      final String forReassign, final String foreachReassign, final String message) {
    this.forReassign = Objects.requireNonNull(forReassign, "forReassign");
    this.foreachReassign = Objects.requireNonNull(foreachReassign, "foreachReassign");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(STATEMENT))) {
      final SyntaxNode loop = visit.node();
      loopVariable(loop).ifPresent(variable -> checkLoop(loop, variable, matches));
    }
    return List.copyOf(matches);
  }

  private void checkLoop(
      final SyntaxNode loop, final SyntaxLoop variable, final List<RuleMatch> matches) {
    final String mode = variable.enhanced() ? foreachReassign : forReassign;
    if (!DENY.equals(mode) && !FIRST_ONLY.equals(mode)) {
      return;
    }
    final SyntaxNode body = loop.children().get(loop.children().size() - 1);
    for (final SyntaxNode assignment : Assignments.in(body)) {
      if (variable.name().equals(Assignments.target(assignment))
          && !(FIRST_ONLY.equals(mode) && isFirstStatement(body, assignment))) {
        matches.add(
            CheckSupport.at(assignment.span(), CheckSupport.fill(message, variable.name(), "")));
      }
    }
  }

  private static boolean isFirstStatement(final SyntaxNode body, final SyntaxNode assignment) {
    return SyntaxWalk.descendants(body).stream()
        .filter(node -> "blockStatement".equals(node.kind()))
        .findFirst()
        .map(first -> SyntaxWalk.descendants(first).contains(assignment))
        .orElse(false);
  }

  private static java.util.Optional<SyntaxLoop> loopVariable(final SyntaxNode loop) {
    for (final SyntaxNode child : loop.children()) {
      if ("forControl".equals(child.kind())) {
        return child.children().stream()
            .filter(c -> "enhancedForControl".equals(c.kind()))
            .findFirst()
            .map(AvoidReassigningLoopVariablesCheck::enhanced)
            .orElseGet(() -> classic(child));
      }
    }
    return java.util.Optional.empty();
  }

  private static java.util.Optional<SyntaxLoop> classic(final SyntaxNode control) {
    return SyntaxWalk.descendants(control).stream()
        .filter(node -> "variableDeclarator".equals(node.kind()))
        .findFirst()
        .flatMap(
            declarator ->
                SyntaxWalk.descendants(declarator).stream()
                    .filter(node -> "identifier".equals(node.kind()))
                    .findFirst())
        .map(id -> new SyntaxLoop(SyntaxWalk.text(id), false));
  }

  private static java.util.Optional<SyntaxLoop> enhanced(final SyntaxNode control) {
    return control.children().stream()
        .filter(child -> "variableDeclaratorId".equals(child.kind()))
        .findFirst()
        .flatMap(id -> id.children().stream().findFirst())
        .map(id -> new SyntaxLoop(SyntaxWalk.text(id), true));
  }

  /** A loop variable: its name, and whether it is an enhanced {@code for} variable. */
  private record SyntaxLoop(String name, boolean enhanced) {}
}
