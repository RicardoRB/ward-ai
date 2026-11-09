package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports a {@code new} method in a class, or a constructor in an interface, that builds the type.
 */
public final class MisusedNewCheck implements RuleCheck {

  private static final String CLASS = "class";
  private static final String INTERFACE = "interface";
  private static final String NEW = "new";
  private static final String CONSTRUCTOR = "constructor";
  private static final String BRACE = "{";
  private static final String ANNOTATION = "typeAnnotation";
  private static final String PROPERTY_NAME = "propertyName";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String CONSTRUCT_SIGNATURE = "constructSignature";
  private static final String METHOD_SIGNATURE = "methodSignature";
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String INTERFACE_DECLARATION = "interfaceDeclaration";
  private static final String MEMBER = "propertyMemberDeclaration";

  private final String classMessage;
  private final String interfaceMessage;

  /** Creates the check with the messages for a class and for an interface. */
  public MisusedNewCheck(final String classMessage, final String interfaceMessage) {
    this.classMessage = classMessage;
    this.interfaceMessage = interfaceMessage;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode declaration : SyntaxText.nodesOf(root, CLASS_DECLARATION)) {
      checkClass(out, declaration);
    }
    for (final SyntaxNode declaration : SyntaxText.nodesOf(root, INTERFACE_DECLARATION)) {
      checkInterface(out, declaration);
    }
    return out;
  }

  private void checkClass(final List<RuleMatch> out, final SyntaxNode declaration) {
    final String name = nameAfter(declaration, CLASS);
    for (final SyntaxNode member : SyntaxText.nodesOf(declaration, MEMBER)) {
      final List<SyntaxNode> signatures = SyntaxText.childrenOf(member, CALL_SIGNATURE);
      if (named(member, NEW)
          && !SyntaxText.hasToken(member, BRACE)
          && signatures.size() == 1
          && returns(signatures.get(0), name)) {
        out.add(new RuleMatch(member.span(), classMessage));
      }
    }
  }

  private void checkInterface(final List<RuleMatch> out, final SyntaxNode declaration) {
    final String name = nameAfter(declaration, INTERFACE);
    for (final SyntaxNode signature : SyntaxText.nodesOf(declaration, CONSTRUCT_SIGNATURE)) {
      if (returns(signature, name)) {
        out.add(new RuleMatch(signature.span(), interfaceMessage));
      }
    }
    for (final SyntaxNode signature : SyntaxText.nodesOf(declaration, METHOD_SIGNATURE)) {
      if (named(signature, CONSTRUCTOR)) {
        out.add(new RuleMatch(signature.span(), interfaceMessage));
      }
    }
  }

  private static boolean named(final SyntaxNode node, final String name) {
    return SyntaxText.childrenOf(node, PROPERTY_NAME).stream()
        .anyMatch(property -> name.equals(SyntaxText.of(property)));
  }

  private static boolean returns(final SyntaxNode signature, final String name) {
    final List<SyntaxNode> annotations = SyntaxText.childrenOf(signature, ANNOTATION);
    return name != null
        && annotations.size() == 1
        && SyntaxText.of(annotations.get(0)).equals(":" + name);
  }

  private static String nameAfter(final SyntaxNode declaration, final String keyword) {
    final List<SyntaxNode> parts = declaration.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (SyntaxText.isToken(parts.get(index), keyword)) {
        return SyntaxText.of(parts.get(index + 1));
      }
    }
    return null;
  }
}
