package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a function that returns a value on some path and returns nothing on another, like Pylint
 * {@code R1710}. A function is consistent when every return has a value and the body always ends in
 * a return, or when no return has a value. A bare {@code return} and a fall-through end both return
 * nothing; {@code return None} has a value. The check reads the syntax tree, as Pylint does.
 */
public final class InconsistentReturnCheck implements RuleCheck {

  private static final String RETURN = "return_stmt";
  private static final String FUNCTION = "funcdef";
  private static final String CLASS = "classdef";
  private static final String BLOCK = "block";
  private static final String IF = "if_stmt";
  private static final String WHILE = "while_stmt";
  private static final String TRY = "try_stmt";
  private static final String ASSERT = "assert_stmt";
  private static final String RAISE = "raise_stmt";
  private static final String BREAK = "break_stmt";
  private static final String ELSE = "else";
  private static final String NAMED_EXPR = "namedexpr_test";
  private static final String EXCEPT = "except_clause";
  private static final Set<String> LOOPS = Set.of("for_stmt", WHILE);
  private static final Set<String> SCOPES = Set.of(FUNCTION, CLASS, "lambdef", "lambdef_nocond");
  private static final Set<String> EXIT_CALLS = Set.of("sys.exit", "os._exit", "exit", "quit");
  private static final Set<String> TRUE_LITERALS = Set.of("True", "1");
  private static final Set<String> FALSE_LITERALS = Set.of("False", "0");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public InconsistentReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Checks every function of the file.
   *
   * @param root the syntax tree of the file
   * @return one finding per inconsistent function, at its definition
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    return matches(root);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (FUNCTION.equals(node.kind()) && inconsistent(node)) {
      found.add(new RuleMatch(node.span(), message));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, found);
    }
  }

  private static boolean inconsistent(final SyntaxNode function) {
    final SyntaxNode body = child(function, BLOCK);
    final List<SyntaxNode> returns = new ArrayList<>();
    collectReturns(body, returns);
    final long explicit = returns.stream().filter(InconsistentReturnCheck::hasValue).count();
    if (explicit == 0) {
      return false;
    }
    return explicit != returns.size() || !endsInReturn(body);
  }

  /**
   * The returns of a body, leaving out the nested functions and classes, which return for
   * themselves.
   */
  private static void collectReturns(final SyntaxNode node, final List<SyntaxNode> returns) {
    if (SCOPES.contains(node.kind())) {
      return;
    }
    if (RETURN.equals(node.kind())) {
      returns.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      collectReturns(child, returns);
    }
  }

  /** A return has a value when it has a child after the {@code return} keyword. */
  private static boolean hasValue(final SyntaxNode ret) {
    return ret.children().stream().anyMatch(child -> !child.children().isEmpty());
  }

  private static boolean endsInReturn(final SyntaxNode block) {
    return anyEnds(statements(block), false);
  }

  /** Reports whether any statement in the list ends the body, given whether a return follows. */
  private static boolean anyEnds(final List<SyntaxNode> statements, final boolean returnAfter) {
    for (int index = 0; index < statements.size(); index++) {
      final boolean later = returnAfter || hasReturnAfter(statements, index);
      if (ends(statements.get(index), later)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasReturnAfter(final List<SyntaxNode> statements, final int index) {
    for (int next = index + 1; next < statements.size(); next++) {
      if (RETURN.equals(statements.get(next).kind())) {
        return true;
      }
    }
    return false;
  }

  /** Reports whether one statement ends the body, the way Pylint's return-ended test does. */
  private static boolean ends(final SyntaxNode node, final boolean returnAfter) {
    if (SCOPES.contains(node.kind())) {
      return false;
    }
    return switch (node.kind()) {
      case RETURN -> true;
      case RAISE -> true;
      case ASSERT -> assertsFalse(node);
      case IF -> ifEnds(node, returnAfter);
      case WHILE -> whileEnds(node);
      case TRY -> tryEnds(node);
      default -> callsExit(node) || anyChildEnds(node);
    };
  }

  private static boolean anyChildEnds(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (ends(child, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean assertsFalse(final SyntaxNode node) {
    final List<SyntaxNode> tokens = SyntaxWalk.leaves(node);
    return tokens.size() > 1 && FALSE_LITERALS.contains(SyntaxWalk.text(tokens.get(1)));
  }

  private static boolean ifEnds(final SyntaxNode node, final boolean returnAfter) {
    final List<SyntaxNode> blocks = children(node, BLOCK);
    final boolean hasElse = node.children().stream().anyMatch(child -> isToken(child, ELSE));
    if (!hasElse) {
      return returnAfter && blocks.stream().anyMatch(InconsistentReturnCheck::endsInReturn);
    }
    return blocks.stream().allMatch(InconsistentReturnCheck::endsInReturn);
  }

  private static boolean whileEnds(final SyntaxNode node) {
    final List<SyntaxNode> blocks = children(node, BLOCK);
    final SyntaxNode condition = child(node, NAMED_EXPR);
    final boolean alwaysTrue = condition != null && TRUE_LITERALS.contains(onlyText(condition));
    final boolean exitsEarly = !blocks.isEmpty() && hasBreak(blocks.get(0));
    final boolean elseEnds = blocks.size() > 1 && endsInReturn(blocks.get(1));
    return (alwaysTrue && !exitsEarly) || elseEnds;
  }

  /** A {@code break} that leaves this loop, not one of a nested loop or scope. */
  private static boolean hasBreak(final SyntaxNode node) {
    if (SCOPES.contains(node.kind()) || LOOPS.contains(node.kind())) {
      return false;
    }
    return BREAK.equals(node.kind())
        || node.children().stream().anyMatch(InconsistentReturnCheck::hasBreak);
  }

  private static boolean tryEnds(final SyntaxNode node) {
    final List<SyntaxNode> handlers = children(node, EXCEPT);
    final List<SyntaxNode> blocks = children(node, BLOCK);
    final boolean bodyEnds =
        blocks.stream().limit(1).anyMatch(InconsistentReturnCheck::endsInReturn);
    final boolean handlersEnd =
        !handlers.isEmpty()
            && blocks.subList(1, 1 + handlers.size()).stream()
                .allMatch(InconsistentReturnCheck::endsInReturn);
    return bodyEnds && handlersEnd;
  }

  /** A call to a function that never returns, such as {@code sys.exit(...)}. */
  private static boolean callsExit(final SyntaxNode node) {
    final List<SyntaxNode> tokens = SyntaxWalk.leaves(node);
    final String head = text(tokens, 0) + text(tokens, 1) + text(tokens, 2) + text(tokens, 3);
    return EXIT_CALLS.stream().anyMatch(call -> head.startsWith(call + "("));
  }

  private static String onlyText(final SyntaxNode node) {
    final List<SyntaxNode> tokens = SyntaxWalk.leaves(node);
    return tokens.size() == 1 ? SyntaxWalk.text(tokens.get(0)) : "";
  }

  private static String text(final List<SyntaxNode> tokens, final int index) {
    return index < tokens.size() ? SyntaxWalk.text(tokens.get(index)) : "";
  }

  private static List<SyntaxNode> statements(final SyntaxNode block) {
    final List<SyntaxNode> statements = new ArrayList<>();
    for (final SyntaxNode child : block.children()) {
      if (!child.children().isEmpty()) {
        statements.add(child);
      }
    }
    return statements;
  }

  private static SyntaxNode child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst().orElse(null);
  }

  private static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).toList();
  }

  private static boolean isToken(final SyntaxNode node, final String text) {
    return node.children().isEmpty() && text.equals(SyntaxWalk.text(node));
  }
}
