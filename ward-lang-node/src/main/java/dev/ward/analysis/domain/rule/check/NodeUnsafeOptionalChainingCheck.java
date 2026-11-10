package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.ExpressionShapes.SINGLE;
import static dev.ward.analysis.domain.rule.check.ExpressionShapes.innerOf;
import static dev.ward.analysis.domain.rule.check.ExpressionShapes.isParenthesized;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an optional chain whose short-circuit to {@code undefined} would throw, or give {@code
 * NaN} in arithmetic, like ESLint {@code no-unsafe-optional-chaining}. A chain is unsafe in an
 * operand position, and a parenthesized chain is unsafe where a member, call, tag or {@code new}
 * takes it.
 */
public final class NodeUnsafeOptionalChainingCheck implements RuleCheck {

  private static final String ITERATION = "iterationStatement";
  private static final String CLASS_TAIL = "classTail";
  private static final String CLASS_EXTENDS = "classExtendsClause";
  private static final String DECLARATION = "variableDeclaration";
  private static final String ARGUMENT = "argument";
  private static final String ARRAY_ELEMENT = "arrayElement";
  private static final String EXPRESSION_SEQUENCE = "expressionSequence";
  private static final String NEW = "new";
  private static final String OF = "of";
  private static final String EXTENDS = "extends";
  private static final String ASSIGN = "=";
  private static final String SPREAD = "...";
  private static final String ARGUMENTS = "arguments";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String DOT = ".";
  private static final String BRACKET = "[";
  private static final String OPTIONAL = "?.";
  private static final String UNARY_MINUS = "-";
  private static final String UNARY_PLUS = "+";
  private static final Set<String> RELATIONAL = Set.of("in", "instanceof");
  private static final Set<String> ARITHMETIC = Set.of("+", "-", "/", "*", "%", "**");
  private static final Set<String> ASSIGNMENT_ARITHMETIC =
      Set.of("+=", "-=", "/=", "*=", "%=", "**=");

  private final boolean disallowArithmetic;
  private final String message;
  private final String arithmeticMessage;

  /**
   * Creates the check.
   *
   * @param disallowArithmetic whether an arithmetic operation on a chain is reported
   * @param message the message for an unsafe chain
   * @param arithmeticMessage the message for an arithmetic operation on a chain
   */
  public NodeUnsafeOptionalChainingCheck(
      final boolean disallowArithmetic, final String message, final String arithmeticMessage) {
    this.disallowArithmetic = disallowArithmetic;
    this.message = Objects.requireNonNull(message, "message");
    this.arithmeticMessage = Objects.requireNonNull(arithmeticMessage, "arithmeticMessage");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (SINGLE.equals(node.kind())) {
      expression(node, matches);
    } else if (ITERATION.equals(node.kind())) {
      forOf(node, matches);
    } else if (CLASS_TAIL.equals(node.kind()) || CLASS_EXTENDS.equals(node.kind())) {
      extendsClause(node, matches);
    } else if (DECLARATION.equals(node.kind())) {
      destructuringInit(node, matches);
    } else if (ARGUMENT.equals(node.kind()) || ARRAY_ELEMENT.equals(node.kind())) {
      spread(node, matches);
    }
    node.children().forEach(child -> collect(child, matches));
  }

  /** The expression forms: calls, members, tags, {@code new}, operations and assignments. */
  private void expression(final SyntaxNode node, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = node.children();
    if (Shapes.isOperation(children)) {
      operation(children, matches);
    }
    if (children.size() > 1 && NEW.equals(LeafText.of(children.get(0)))) {
      checkOperand(children.get(1), message, matches);
    }
    accessOrCall(children, matches);
  }

  /** A member, call or tag whose object is parenthesized: the parentheses end the chain. */
  private void accessOrCall(final List<SyntaxNode> children, final List<RuleMatch> matches) {
    if (children.size() < Shapes.PREFIX_SIZE || !SINGLE.equals(children.get(0).kind())) {
      return;
    }
    final SyntaxNode second = children.get(1);
    final boolean access =
        ARGUMENTS.equals(second.kind())
            || TEMPLATE.equals(second.kind())
            || DOT.equals(LeafText.of(second))
            || BRACKET.equals(LeafText.of(second));
    if (access && isParenthesized(children.get(0))) {
      checkOperand(children.get(0), message, matches);
    }
  }

  /** A unary plus or minus, or a binary, assignment or relational operation on its operands. */
  private void operation(final List<SyntaxNode> children, final List<RuleMatch> matches) {
    final Shapes.Checked checked =
        Shapes.checked(children, disallowArithmetic, message, arithmeticMessage);
    checked.operands().forEach(operand -> checkOperand(operand, checked.text(), matches));
  }

  /**
   * The operand of a position: its logical, conditional or await parts are operands in turn, and a
   * chain with an optional access is reported where it starts. Anything else is not checked.
   */
  private void checkOperand(
      final SyntaxNode operand, final String text, final List<RuleMatch> matches) {
    if (!SINGLE.equals(operand.kind())) {
      return;
    }
    final SyntaxNode inner = innerOf(operand);
    final List<SyntaxNode> parts = Shapes.subOperands(inner);
    if (parts.isEmpty()) {
      if (Shapes.chainHasOptional(inner)) {
        matches.add(new RuleMatch(inner.span(), text));
      }
      return;
    }
    parts.forEach(part -> checkOperand(part, text, matches));
  }

  private void forOf(final SyntaxNode statement, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = statement.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (OF.equals(LeafText.of(children.get(index)))
          && EXPRESSION_SEQUENCE.equals(children.get(index + 1).kind())) {
        children.get(index + 1).children().forEach(child -> checkOperand(child, message, matches));
      }
    }
  }

  /** The superclass of a class; a TypeScript heritage holds a type reference instead. */
  private void extendsClause(final SyntaxNode clause, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = clause.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (EXTENDS.equals(LeafText.of(children.get(index)))) {
        final SyntaxNode superclass = children.get(index + 1);
        if (Shapes.isTypeReference(superclass)) {
          Shapes.reportTypeReference(superclass, message, matches);
        } else {
          checkOperand(superclass, message, matches);
        }
      }
    }
  }

  private void destructuringInit(final SyntaxNode declaration, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = declaration.children();
    if (Shapes.isPattern(declaration) && children.size() == Shapes.BINARY_SIZE) {
      checkOperand(children.get(2), message, matches);
    }
  }

  private void spread(final SyntaxNode element, final List<RuleMatch> matches) {
    final List<SyntaxNode> children = element.children();
    if (children.size() == Shapes.PREFIX_SIZE && SPREAD.equals(LeafText.of(children.get(0)))) {
      checkOperand(children.get(1), message, matches);
    }
  }

  /** The tree queries of optional chains, patterns and operands. */
  private static final class Shapes {

    static final int PREFIX_SIZE = 2;
    static final int BINARY_SIZE = 3;
    private static final int CONDITIONAL_SIZE = 5;
    private static final int CONSEQUENT = 2;
    private static final int ALTERNATE = 4;
    private static final String ARRAY_LITERAL = "arrayLiteral";
    private static final String OBJECT_LITERAL = "objectLiteral";
    private static final String ASSIGNABLE = "assignable";
    private static final String TYPE_REFERENCE = "typeReference";
    private static final String QUESTION = "?";
    private static final String AWAIT = "await";
    private static final String AND = "&&";
    private static final String ASSIGNMENT_OPERATOR = "assignmentOperator";
    private static final Set<String> SHORT_CIRCUIT_RIGHT = Set.of("||", "??");

    /** An operation is a unary operator with its operand, or a binary one with its two sides. */
    /** The operands an operation checks, and the message for them. */
    record Checked(List<SyntaxNode> operands, String text) {}

    /** Which operands of an operation are checked: its sides, under the message of its kind. */
    static Checked checked(
        final List<SyntaxNode> children,
        final boolean disallowArithmetic,
        final String message,
        final String arithmeticMessage) {
      return children.size() == PREFIX_SIZE
          ? unary(children, disallowArithmetic, message, arithmeticMessage)
          : binary(children, disallowArithmetic, message, arithmeticMessage);
    }

    private static Checked unary(
        final List<SyntaxNode> children,
        final boolean disallowArithmetic,
        final String message,
        final String arithmeticMessage) {
      final String op = LeafText.of(children.get(0));
      final boolean sign = UNARY_MINUS.equals(op) || UNARY_PLUS.equals(op);
      return disallowArithmetic && sign
          ? new Checked(List.of(children.get(1)), arithmeticMessage)
          : new Checked(List.of(), message);
    }

    private static Checked binary(
        final List<SyntaxNode> children,
        final boolean disallowArithmetic,
        final String message,
        final String arithmeticMessage) {
      final String op = LeafText.of(children.get(1));
      final SyntaxNode left = children.get(0);
      final SyntaxNode right = children.get(2);
      if (RELATIONAL.contains(op)) {
        return new Checked(List.of(right), message);
      }
      if (disallowArithmetic && ARITHMETIC.contains(op)) {
        return new Checked(List.of(left, right), arithmeticMessage);
      }
      if (disallowArithmetic && ASSIGNMENT_ARITHMETIC.contains(op)) {
        return new Checked(List.of(right), arithmeticMessage);
      }
      if (ASSIGN.equals(op) && isPattern(left)) {
        return new Checked(List.of(right), message);
      }
      return new Checked(List.of(), message);
    }

    static boolean isOperation(final List<SyntaxNode> children) {
      return children.size() == PREFIX_SIZE && isOperator(children.get(0))
          || children.size() == BINARY_SIZE && isOperator(children.get(1));
    }

    /** An operator is a token, or the assignment operator node that holds one. */
    static boolean isOperator(final SyntaxNode node) {
      return node.children().isEmpty() || ASSIGNMENT_OPERATOR.equals(node.kind());
    }

    /** A destructuring target: an array or object literal, held directly or in an assignable. */
    static boolean isPattern(final SyntaxNode target) {
      return target.children().stream()
          .anyMatch(
              child ->
                  isPatternKind(child)
                      || ASSIGNABLE.equals(child.kind())
                          && child.children().stream().anyMatch(Shapes::isPatternKind));
    }

    private static boolean isPatternKind(final SyntaxNode node) {
      return ARRAY_LITERAL.equals(node.kind()) || OBJECT_LITERAL.equals(node.kind());
    }

    static boolean isTypeReference(final SyntaxNode node) {
      return TYPE_REFERENCE.equals(node.kind());
    }

    /** A TypeScript heritage is a type reference; it is reported when its text has an optional. */
    static void reportTypeReference(
        final SyntaxNode reference, final String text, final List<RuleMatch> matches) {
      if (LeafText.of(reference).contains(OPTIONAL)) {
        matches.add(new RuleMatch(reference.span(), text));
      }
    }

    /** The operands a logical, conditional or await expression passes its value through. */
    static List<SyntaxNode> subOperands(final SyntaxNode inner) {
      final List<SyntaxNode> children = inner.children();
      final List<SyntaxNode> parts = new ArrayList<>();
      if (children.size() == BINARY_SIZE) {
        final String op = LeafText.of(children.get(1));
        if (SHORT_CIRCUIT_RIGHT.contains(op)) {
          parts.add(children.get(2));
        } else if (AND.equals(op)) {
          parts.add(children.get(0));
          parts.add(children.get(2));
        }
      } else if (children.size() == CONDITIONAL_SIZE
          && QUESTION.equals(LeafText.of(children.get(1)))) {
        parts.add(children.get(CONSEQUENT));
        parts.add(children.get(ALTERNATE));
      } else if (children.size() == PREFIX_SIZE && AWAIT.equals(LeafText.of(children.get(0)))) {
        parts.add(children.get(1));
      }
      return parts;
    }

    /** Whether a chain has an optional access at its own level or below it, without parentheses. */
    static boolean chainHasOptional(final SyntaxNode expression) {
      final List<SyntaxNode> children = expression.children();
      if (children.size() < PREFIX_SIZE || isParenthesized(expression)) {
        return false;
      }
      final SyntaxNode second = children.get(1);
      if (OPTIONAL.equals(LeafText.of(second))) {
        return true;
      }
      final boolean access =
          ARGUMENTS.equals(second.kind())
              || TEMPLATE.equals(second.kind())
              || DOT.equals(LeafText.of(second))
              || BRACKET.equals(LeafText.of(second));
      return access && SINGLE.equals(children.get(0).kind()) && chainHasOptional(children.get(0));
    }
  }
}
