package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the body shapes that Pylint flags in a function: a constructor that returns a value other
 * than {@code None} ({@code return-in-init}), a method whose whole body calls the same method of
 * {@code super()} with its own positional arguments ({@code useless-parent-delegation}), and a name
 * used in a function before the {@code global} statement that declares it ({@code
 * used-prior-global-declaration}). Nested functions, classes and lambdas are not entered.
 */
public final class MethodBodyCheck implements RuleCheck {

  /** The problem a function is reported for. */
  public enum Problem {
    /** A constructor returns a value other than None. */
    RETURN_IN_INIT,
    /** A method only calls the same method of {@code super()} with its own arguments. */
    USELESS_PARENT_DELEGATION,
    /** A name is used before the global statement that declares it. */
    USED_PRIOR_GLOBAL
  }

  private static final String FUNCTION = "funcdef";
  private static final String CLASS = "classdef";
  private static final String LAMBDA = "lambdef";
  private static final String INIT = "__init__";
  private static final String NONE = "None";
  private static final String RETURN = "return";
  private static final String GLOBAL = "global";
  private static final Set<String> NESTED = Set.of(FUNCTION, CLASS, LAMBDA);
  private static final String IDENTIFIER = "[A-Za-z_]\\w*";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the problem to report
   * @param message the message of each finding
   */
  public MethodBodyCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FUNCTION))) {
      final SyntaxNode function = visit.node();
      final boolean method = visit.ancestors().stream().anyMatch(a -> CLASS.equals(a.kind()));
      if (problem == Problem.RETURN_IN_INIT) {
        if (method && INIT.equals(nameOf(function)) && returnsValue(function)) {
          found.add(new RuleMatch(function.span(), message));
        }
      } else if (problem == Problem.USELESS_PARENT_DELEGATION) {
        if (method && delegatesOnly(function)) {
          found.add(new RuleMatch(function.span(), message));
        }
      } else {
        usesBeforeGlobal(function, found, message);
      }
    }
    return List.copyOf(found);
  }

  private static String nameOf(final SyntaxNode definition) {
    return ModuleDefinitions.definedName(definition).orElse("");
  }

  /** Reports a return with a value other than the literal None, in the function itself. */
  private static boolean returnsValue(final SyntaxNode function) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(function, Set.of("return_stmt"))) {
      if (!nested(visit.ancestors())) {
        final String value =
            SyntaxWalk.text(visit.node()).strip().substring(RETURN.length()).strip();
        if (!value.isEmpty() && !NONE.equals(value)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Reports a body that is only {@code super().name(self-less args)} with the same names. */
  private static boolean delegatesOnly(final SyntaxNode function) {
    final Parameters parameters = Parameters.of(function);
    if (parameters.varargs() || parameters.positional().isEmpty()) {
      return false;
    }
    final List<String> rest = parameters.positional().subList(1, parameters.positional().size());
    final String plain = "super()." + nameOf(function) + "(" + String.join(",", rest) + ")";
    final String body =
        SyntaxWalk.text(function.children().get(function.children().size() - 1)).strip();
    return body.equals(plain) || body.equals(RETURN + plain);
  }

  /** Reports each name of the function's own body used before a global statement declares it. */
  private static void usesBeforeGlobal(
      final SyntaxNode function, final List<RuleMatch> found, final String message) {
    final List<String> globals = new ArrayList<>();
    final List<Integer> lines = new ArrayList<>();
    declaredGlobals(function, globals, lines);
    if (globals.isEmpty()) {
      return;
    }
    for (final SyntaxNode leaf : ownLeaves(function, true)) {
      final int index = globals.indexOf(SyntaxWalk.text(leaf));
      if (index >= 0
          && SyntaxWalk.text(leaf).matches(IDENTIFIER)
          && leaf.span().startLine() < lines.get(index)) {
        found.add(new RuleMatch(leaf.span(), message));
      }
    }
  }

  /** Collects the names of the global statements of a function, with the line of each statement. */
  private static void declaredGlobals(
      final SyntaxNode function, final List<String> globals, final List<Integer> lines) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(function, Set.of("global_stmt"))) {
      if (!nested(visit.ancestors())) {
        for (final SyntaxNode leaf : SyntaxWalk.leaves(visit.node())) {
          if (!GLOBAL.equals(SyntaxWalk.text(leaf))) {
            globals.add(SyntaxWalk.text(leaf));
            lines.add(visit.node().span().startLine());
          }
        }
      }
    }
  }

  /** The text leaves of a node, not entering nested functions, classes or lambdas. */
  private static List<SyntaxNode> ownLeaves(final SyntaxNode node, final boolean top) {
    final List<SyntaxNode> out = new ArrayList<>();
    if (!top && NESTED.contains(node.kind())) {
      return out;
    }
    if (node.children().isEmpty()) {
      if (node.fields().containsKey("text")) {
        out.add(node);
      }
      return out;
    }
    for (final SyntaxNode child : node.children()) {
      out.addAll(ownLeaves(child, false));
    }
    return out;
  }

  /**
   * Reports whether a node between the function (first ancestor) and the node is a nested scope.
   */
  private static boolean nested(final List<SyntaxNode> ancestors) {
    return ancestors.stream()
        .skip(1)
        .anyMatch(
            a -> FUNCTION.equals(a.kind()) || CLASS.equals(a.kind()) || LAMBDA.equals(a.kind()));
  }
}
