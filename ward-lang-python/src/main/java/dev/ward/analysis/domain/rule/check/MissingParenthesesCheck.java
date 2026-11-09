package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an {@code if}, {@code elif}, {@code while} or {@code assert} whose condition is a bare
 * name bound to a function or lambda of this module, like pylint {@code
 * missing-parentheses-for-call-in-test}: the function is never called. A condition with an operator
 * is not judged.
 */
public final class MissingParenthesesCheck implements RuleCheck {

  private static final String DEF = "def";
  private static final Set<String> TESTS = Set.of("if_stmt", "while_stmt", "assert_stmt");
  private static final Set<String> SCOPES = Set.of("funcdef", "classdef", "lambdef");
  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> ASSIGNMENTS = Set.of("expr_stmt");
  private static final Pattern CONDITION =
      Pattern.compile("^(?:if|elif|while|assert)(\\w+)(?=[:,]|$)");
  private static final Pattern LAMBDA = Pattern.compile("^(\\w+)=lambda");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public MissingParenthesesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> callables = moduleCallables(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TESTS)) {
      final Matcher condition = CONDITION.matcher(SyntaxWalk.text(visit.node()));
      if (condition.find() && callables.contains(condition.group(1))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** The names of the functions and lambda-valued names bound at the top level of the module. */
  private static Set<String> moduleCallables(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      if (topLevel(visit.ancestors())) {
        names.add(functionName(visit.node()));
      }
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, ASSIGNMENTS)) {
      final Matcher lambda = LAMBDA.matcher(SyntaxWalk.text(visit.node()));
      if (topLevel(visit.ancestors()) && lambda.find()) {
        names.add(lambda.group(1));
      }
    }
    return Set.copyOf(names);
  }

  private static boolean topLevel(final List<SyntaxNode> ancestors) {
    return ancestors.stream().noneMatch(node -> SCOPES.contains(node.kind()));
  }

  private static String functionName(final SyntaxNode function) {
    final List<SyntaxNode> children = function.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (DEF.equals(SyntaxWalk.text(children.get(index)))) {
        return SyntaxWalk.text(children.get(index + 1));
      }
    }
    return "";
  }
}
