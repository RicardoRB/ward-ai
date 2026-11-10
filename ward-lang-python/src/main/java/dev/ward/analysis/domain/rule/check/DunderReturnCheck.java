package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code return} of a literal whose type a special method must not return, like pylint
 * {@code invalid-str-returned}. The method is the one named in the rule. A returned value that is
 * not a literal has an unknown type and is not reported; a method without a {@code return} is not
 * reported either.
 *
 * <p>Shortcut: the implicit {@code None} of a method that never returns is not judged, and tuples
 * are judged by their outer brackets only; upgrade with body analysis when type facts cover them.
 */
public final class DunderReturnCheck implements RuleCheck {

  /** The literal type a returned value has, when its text shows it. */
  public enum Literal {
    NONE,
    BOOL,
    INT,
    FLOAT,
    STR,
    BYTES,
    LIST,
    TUPLE,
    DICT,
    SET,
    UNKNOWN
  }

  private static final Set<String> SCOPES =
      Set.of("funcdef", "classdef", "async_funcdef", "lambdef");
  private static final Set<String> RETURNS = Set.of("return_stmt");
  private static final String DEF = "def";
  private static final String RETURN = "return";

  private final String method;
  private final Literal expected;
  private final boolean nonNegative;
  private final String message;

  /**
   * Creates the check.
   *
   * @param method the special method name, such as {@code __str__}
   * @param expected the literal type the method must return
   * @param nonNegative whether an integer must not be negative, as {@code __len__} requires
   * @param message the message of each finding
   */
  public DunderReturnCheck(
      final String method,
      final Literal expected,
      final boolean nonNegative,
      final String message) {
    this.method = Objects.requireNonNull(method, "method");
    this.expected = Objects.requireNonNull(expected, "expected");
    this.nonNegative = nonNegative;
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Parses the {@code returns} option of a rule into the literal type it names. */
  public static Literal parseExpected(final String name) {
    return Literal.valueOf(name.toUpperCase(Locale.ROOT));
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit scope : SyntaxWalk.visits(root, Set.of("funcdef"))) {
      if (method.equals(methodName(scope.node()))) {
        addWrongReturns(scope.node(), found);
      }
    }
    return List.copyOf(found);
  }

  private void addWrongReturns(final SyntaxNode function, final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit ret : SyntaxWalk.visits(function, RETURNS)) {
      if (!nestedScope(ret.ancestors()) && violates(returnedText(ret.node()))) {
        found.add(new RuleMatch(ret.node().span(), message));
      }
    }
  }

  /** True when the return belongs to a function or class nested in the method. */
  private static boolean nestedScope(final List<SyntaxNode> ancestors) {
    return ancestors.stream().skip(1).anyMatch(node -> SCOPES.contains(node.kind()));
  }

  private boolean violates(final String value) {
    final Literal actual = ReturnLiteral.classify(value);
    if (actual == Literal.UNKNOWN) {
      return false;
    }
    final boolean negative =
        nonNegative && expected == Literal.INT && actual == Literal.INT && value.startsWith("-");
    return negative || (actual != expected && !(expected == Literal.INT && actual == Literal.BOOL));
  }

  private static String methodName(final SyntaxNode function) {
    final List<SyntaxNode> children = function.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (DEF.equals(SyntaxWalk.text(children.get(index)))) {
        return SyntaxWalk.text(children.get(index + 1));
      }
    }
    return "";
  }

  /** The text after the {@code return} keyword; empty for a bare return, which is {@code None}. */
  private static String returnedText(final SyntaxNode ret) {
    final String text = SyntaxWalk.text(ret).strip();
    return text.startsWith(RETURN) ? text.substring(RETURN.length()).strip() : text;
  }
}
