package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code no-unsafe-*} checks that report an {@code any}-typed value used where its type is
 * lost: a call, a member access, a returned value, an argument, a declaration or a negation.
 */
public final class UnsafeAnyCheck extends TypedCheck {

  /** The places where an {@code any} value is unsafe. */
  public enum Site {
    /** The callee of a call. */
    CALL,
    /** The object of a member access. */
    MEMBER,
    /** The value of a return statement. */
    RETURN,
    /** An argument of a call. */
    ARGUMENT,
    /** The initializer of a declaration with an explicit type. */
    ASSIGNMENT,
    /** The operand of a unary minus. */
    NEGATION
  }

  private static final String RETURN_STATEMENT = "returnStatement";
  private static final String ARGUMENTS = "argument";
  private static final String MINUS = "-";
  private static final String DECLARATION = "variableDeclaration";
  private static final String ANNOTATION = "typeAnnotation";

  private final Site site;
  private final String message;

  /** Creates the check for one site with the message of the rule. */
  public UnsafeAnyCheck(final Site site, final String message) {
    this.site = site;
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    switch (site) {
      case CALL, MEMBER, NEGATION -> collectExpressions(root, types, out);
      case RETURN -> collectReturns(root, types, out);
      case ARGUMENT -> collectArguments(root, types, out);
      case ASSIGNMENT -> collectDeclarations(root, types, out);
      default -> {}
    }
    return out;
  }

  private void collectExpressions(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      final SyntaxNode subject = subjectOf(parts);
      if (subject != null && isAny(types, subject)) {
        out.add(new RuleMatch(node.span(), message));
      }
    }
  }

  /** Returns the operand whose type the site reads, or null when this node is another form. */
  private SyntaxNode subjectOf(final List<SyntaxNode> parts) {
    return switch (site) {
      case CALL ->
          parts.size() == 2 && "arguments".equals(parts.get(1).kind()) ? parts.get(0) : null;
      case MEMBER ->
          parts.size() == 3
                  && SyntaxText.isToken(parts.get(1), ".")
                  && EXPRESSION.equals(parts.get(0).kind())
              ? parts.get(0)
              : null;
      case NEGATION ->
          parts.size() == 2 && SyntaxText.isToken(parts.get(0), MINUS) ? parts.get(1) : null;
      default -> null;
    };
  }

  private void collectReturns(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, RETURN_STATEMENT)) {
      for (final SyntaxNode value : SyntaxText.childrenOf(statement, SEQUENCE)) {
        if (isAny(types, value)) {
          out.add(new RuleMatch(value.span(), message));
        }
      }
    }
  }

  private void collectArguments(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode argument : SyntaxText.nodesOf(root, ARGUMENTS)) {
      final Optional<SyntaxNode> value =
          argument.children().stream().filter(child -> EXPRESSION.equals(child.kind())).findFirst();
      if (value.isPresent() && isAny(types, value.get())) {
        out.add(new RuleMatch(value.get().span(), message));
      }
    }
  }

  private void collectDeclarations(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode declaration : SyntaxText.nodesOf(root, DECLARATION)) {
      final List<SyntaxNode> annotations = SyntaxText.childrenOf(declaration, ANNOTATION);
      final SyntaxNode init = SyntaxText.last(declaration);
      if (annotations.size() == 1
          && declaration.children().size() == 4
          && !"any".equals(SyntaxText.of(annotations.get(0)).replace(":", ""))
          && !"unknown".equals(SyntaxText.of(annotations.get(0)).replace(":", ""))
          && isAny(types, init)) {
        out.add(new RuleMatch(init.span(), message));
      }
    }
  }

  private static boolean isAny(final UnitTypeFacts types, final SyntaxNode node) {
    return node != null && typeOf(types, node).filter(TypedCheck::isAny).isPresent();
  }
}
