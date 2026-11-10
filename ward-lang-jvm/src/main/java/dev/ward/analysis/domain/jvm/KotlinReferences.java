package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Collects the name references of Kotlin syntax: type positions, call chains, callable references,
 * annotations and names in string templates. Declarations are not collected here.
 */
final class KotlinReferences {
  private static final String IDENTIFIER = "identifier";
  private static final String USER_TYPE = "userType";
  private static final String CALLABLE_REFERENCE = "callableReference";
  private static final String UNESCAPED_ANNOTATION = "unescapedAnnotation";
  private static final String ANNOTATION = "annotation";
  private static final String USE_SITE_TARGET = "annotationUseSiteTarget";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String POSTFIX_OPERATION = "postfixUnaryOperation";
  private static final String LINE_STRING_REF = "LineStrRef";
  private static final String MULTI_LINE_STRING_REF = "MultiLineStrRef";

  private final List<Reference> references;

  /** Creates the collector that appends to {@code references}. */
  KotlinReferences(final List<Reference> references) {
    this.references = references;
  }

  /** Adds the references of {@code node} and of everything under it. */
  void visit(final SyntaxNode node) {
    if (POSTFIX.equals(node.kind())) {
      postfix(node, true);
      return;
    }
    reference(node);
    for (final SyntaxNode child : node.children()) {
      visit(child);
    }
  }

  /** Adds the reference that {@code node} itself names, if it names one. */
  private void reference(final SyntaxNode node) {
    switch (node.kind()) {
      case USER_TYPE -> addName(node, KotlinSyntax.userTypeName(node));
      case CALLABLE_REFERENCE -> callable(node);
      case UNESCAPED_ANNOTATION -> unescaped(node);
      case ANNOTATION -> annotation(node);
      case LINE_STRING_REF, MULTI_LINE_STRING_REF ->
          addName(node, JavaSyntax.textOf(node).substring(1));
      default -> {
        // Other nodes name nothing themselves.
      }
    }
  }

  /**
   * A postfix expression names its leading dotted name. A navigation call such as {@code
   * x.toMoney()} names the called member. A nested chain is read once, with the name of its outer
   * expression.
   */
  private void postfix(final SyntaxNode node, final boolean named) {
    if (named) {
      addName(node, KotlinNameChain.of(node));
    } else if (KotlinSyntax.startsWithCall(node)) {
      addMember(node, KotlinSyntax.atomName(node));
    }
    for (final SyntaxNode child : node.children()) {
      if (POSTFIX_OPERATION.equals(child.kind())) {
        operation(child);
      } else {
        visit(child);
      }
    }
  }

  /** Reads an operation; a member access carries the nested postfix expression of the member. */
  private void operation(final SyntaxNode operation) {
    for (final SyntaxNode part : operation.children()) {
      if (POSTFIX.equals(part.kind())) {
        postfix(part, false);
      } else {
        visit(part);
      }
    }
  }

  /**
   * A callable reference names its declaration unless it starts with a type, which is read apart.
   */
  private void callable(final SyntaxNode node) {
    if (KotlinSyntax.children(node, USER_TYPE).isEmpty()) {
      KotlinSyntax.children(node, IDENTIFIER)
          .forEach(identifier -> addName(node, KotlinSyntax.segments(identifier)));
    }
  }

  private void unescaped(final SyntaxNode node) {
    KotlinSyntax.children(node, IDENTIFIER)
        .forEach(identifier -> addName(node, KotlinSyntax.segments(identifier)));
  }

  /** An annotation with a use-site target names the annotation in the nested node instead. */
  private void annotation(final SyntaxNode node) {
    if (KotlinSyntax.children(node, USE_SITE_TARGET).isEmpty()) {
      addName(node, KotlinNameChain.of(node));
    }
  }

  private void addName(final SyntaxNode node, final String name) {
    if (!name.isEmpty()) {
      references.add(new Reference("name:" + name, node.span()));
    }
  }

  private void addMember(final SyntaxNode node, final String name) {
    if (!name.isEmpty()) {
      references.add(new Reference("member:" + name, node.span()));
    }
  }
}
