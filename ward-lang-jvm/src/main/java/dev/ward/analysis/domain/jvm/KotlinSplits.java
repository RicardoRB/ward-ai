package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds the parts of a statement that are lowered into blocks of their own: the lambdas, and the
 * {@code if}, {@code when} and {@code try} that make a value, and the operands of {@code &&},
 * {@code ||} and {@code ?:}. The outermost ones are found; a part inside a part is found when that
 * part's statements are lowered. A lambda written as the trailing block of a call also reports the
 * call's name, its label, and the span of the receiver written before the call.
 */
final class KotlinSplits {

  private static final String LAMBDA = "functionLiteral";
  private static final String CALL_SUFFIX = "callSuffix";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String MEMBER = "memberAccessOperator";
  private static final String ANNOTATED = "annotatedLambda";
  private static final Set<String> CONTROL =
      Set.of("ifExpression", "whenExpression", "tryExpression");
  private static final Set<String> OPERATORS =
      Set.of("conjunction", "disjunction", "elvisExpression");
  private static final Set<String> OTHER_BODIES =
      Set.of(
          "functionDeclaration",
          "classDeclaration",
          "objectDeclaration",
          "objectLiteral",
          "companionObject",
          "secondaryConstructor",
          "anonymousInitializer",
          "typeAlias");

  private KotlinSplits() {}

  /**
   * One part of a statement.
   *
   * @param node the part
   * @param callee the name of the call that the part is the trailing block of, or empty
   * @param label the label of the lambda, or empty
   * @param receiver where the receiver of that call is written, or null when it has none
   */
  record Split(SyntaxNode node, String callee, String label, Span receiver) {

    /** Whether the part is a lambda, not an {@code if}, {@code when}, {@code try} or operator. */
    boolean isLambda() {
      return LAMBDA.equals(node.kind());
    }

    /** The span of the part. */
    Span span() {
      return node.span();
    }
  }

  /**
   * Finds the parts of a statement.
   *
   * @param statement a statement or an expression
   * @return the outermost parts, in source order
   */
  static List<Split> of(final SyntaxNode statement) {
    final List<Split> found = new ArrayList<>();
    if (POSTFIX.equals(statement.kind())) {
      chain(statement, null, found);
    } else {
      statement.children().forEach(child -> walk(child, found));
    }
    return found;
  }

  private static void walk(final SyntaxNode node, final List<Split> found) {
    if (OTHER_BODIES.contains(node.kind())) {
      return;
    }
    if (LAMBDA.equals(node.kind()) || isConstruct(node)) {
      found.add(new Split(node, "", "", null));
    } else if (POSTFIX.equals(node.kind())) {
      chain(node, null, found);
    } else {
      node.children().forEach(child -> walk(child, found));
    }
  }

  /** An operator chain with one operand is a wrapper; {@code if}, {@code when}, {@code try} not. */
  static boolean isConstruct(final SyntaxNode node) {
    return CONTROL.contains(node.kind())
        || OPERATORS.contains(node.kind()) && KotlinFlowSyntax.parts(node).size() > 1;
  }

  /** One atom and its operations; {@code start} is where the whole chain begins, once nested. */
  private static void chain(final SyntaxNode node, final Span start, final List<Split> found) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(node);
    final SyntaxNode atom = parts.get(0);
    final Span origin = start == null ? node.span() : start;
    final boolean named = !KotlinCalls.nameOf(atom).isEmpty();
    if (!named) {
      walk(atom, found);
    }
    String callee = KotlinCalls.nameOf(atom);
    for (final SyntaxNode operation : parts.subList(1, parts.size())) {
      final SyntaxNode suffix = KotlinFlowSyntax.child(operation, CALL_SUFFIX);
      final SyntaxNode member = KotlinFlowSyntax.child(operation, MEMBER);
      if (suffix != null) {
        suffix(suffix, callee, start == null ? null : before(origin, atom.span()), found);
      } else if (member != null) {
        chain(KotlinFlowSyntax.child(operation, POSTFIX), origin, found);
      } else {
        operation.children().forEach(child -> walk(child, found));
      }
      callee = "";
    }
  }

  /** The arguments are parts of the statement; the trailing lambdas belong to the call. */
  private static void suffix(
      final SyntaxNode suffix, final String callee, final Span receiver, final List<Split> found) {
    for (final SyntaxNode part : KotlinFlowSyntax.parts(suffix)) {
      if (ANNOTATED.equals(part.kind())) {
        final SyntaxNode lambda = KotlinFlowSyntax.child(part, LAMBDA);
        final SyntaxNode mark = KotlinFlowSyntax.child(part, "labelDefinition");
        found.add(new Split(lambda, callee, labelOf(mark), receiver));
      } else {
        walk(part, found);
      }
    }
  }

  private static String labelOf(final SyntaxNode mark) {
    if (mark == null) {
      return "";
    }
    final String written = KotlinFlowSyntax.text(mark);
    return written.endsWith("@") ? written.substring(0, written.length() - 1) : written;
  }

  /** The span from the start of {@code first} up to the start of {@code last}. */
  private static Span before(final Span first, final Span last) {
    return new Span(
        first.startLine(),
        first.startColumn(),
        last.startLine(),
        last.startColumn(),
        first.startOffset(),
        last.startOffset());
  }
}
