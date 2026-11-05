package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds the flow sites of one Java body: calls and member reads, with the receiver type of their
 * qualifier, and the parameters that carry an annotation. Inner sites come before the sites that
 * contain them, and a lambda or class body is another body, so it is not entered.
 *
 * <p>shortcut: a variable's type is the last declaration of its name seen in the body, not the one
 * in scope, so block-scoped names that are reused with different types may resolve wrongly; upgrade
 * by reusing the scopes of {@link JavaAccesses} when a rule needs it.
 */
final class JavaFlowSites implements FlowSites {

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String IDENTIFIER = "identifier";
  private static final String DOT = ".";
  private static final int MEMBER_PARTS = 3;
  private static final String VARIABLE_DECLARATORS = "variableDeclarators";
  private static final String VARIABLE_DECLARATOR = "variableDeclarator";
  private static final String VARIABLE_DECLARATOR_ID = "variableDeclaratorId";
  private static final String TYPE_TYPE = "typeType";
  private static final String FORMAL_PARAMETERS = "formalParameters";
  private static final String LAMBDA_PARAMETERS = "lambdaParameters";
  private static final String LOCAL_VARIABLE = "localVariableDeclaration";
  private static final String ARGUMENTS = "arguments";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final Set<String> OPAQUE = Set.of("lambdaExpression", "classBody");

  private final JavaTypeNames types;
  private final Predicate<SyntaxNode> skipped;
  private final Map<String, String> declared = new HashMap<>();
  private final JavaParameterSites parameters;

  /**
   * Creates the finder of one body.
   *
   * @param types the imports and package of the file
   * @param skipped reports the nodes that the lowering added as blocks of their own
   */
  JavaFlowSites(final JavaTypeNames types, final Predicate<SyntaxNode> skipped) {
    this.types = types;
    this.skipped = skipped;
    this.parameters = new JavaParameterSites(types, declared);
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (skipped.test(node) || OPAQUE.contains(node.kind())) {
      return;
    }
    switch (node.kind()) {
      case EXPRESSION -> expression(node, found);
      case METHOD_CALL -> call(node, "", found);
      case FORMAL_PARAMETERS, LAMBDA_PARAMETERS -> found.addAll(parameters.of(node));
      case LOCAL_VARIABLE -> localVariable(node, found);
      default -> children(node, found);
    }
  }

  private void children(final SyntaxNode node, final List<FlowSite> found) {
    for (final SyntaxNode child : node.children()) {
      walk(child, found);
    }
  }

  /** A dotted expression is a member read, or a call when the member is a method call. */
  private void expression(final SyntaxNode node, final List<FlowSite> found) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(node);
    if (parts.size() != MEMBER_PARTS || !DOT.equals(JavaFlowSyntax.text(parts.get(1)))) {
      children(node, found);
      return;
    }
    final SyntaxNode receiver = parts.get(0);
    final SyntaxNode member = parts.get(2);
    final String qualifier = JavaFlowSyntax.text(receiver);
    if (METHOD_CALL.equals(member.kind())) {
      walk(receiver, found);
      call(member, qualifier, found);
    } else if (IDENTIFIER.equals(member.kind())) {
      walk(receiver, found);
      found.add(
          new FlowSite(
              FlowSiteKind.MEMBER_READ,
              JavaFlowSyntax.text(member),
              qualifier,
              receiverType(qualifier),
              List.of(),
              List.of(),
              node.span()));
    } else {
      children(node, found);
    }
  }

  /** The arguments come first, so a call inside an argument is listed before this call. */
  private void call(final SyntaxNode call, final String qualifier, final List<FlowSite> found) {
    final List<SyntaxNode> arguments = arguments(call);
    arguments.forEach(argument -> walk(argument, found));
    found.add(
        new FlowSite(
            FlowSiteKind.CALL,
            JavaFlowSyntax.text(JavaSyntaxNames.identifierOf(call)),
            qualifier,
            receiverType(qualifier),
            List.of(),
            spans(arguments),
            call.span()));
  }

  private static List<SyntaxNode> arguments(final SyntaxNode call) {
    final SyntaxNode arguments = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(call), ARGUMENTS);
    final SyntaxNode list =
        arguments == null
            ? null
            : JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(arguments), EXPRESSION_LIST);
    return list == null ? List.of() : JavaFlowSyntax.allOf(JavaFlowSyntax.parts(list), EXPRESSION);
  }

  private static List<Span> spans(final List<SyntaxNode> nodes) {
    return nodes.stream().map(SyntaxNode::span).toList();
  }

  /** Records the declared types of the variables of a declaration, after its initializers. */
  private void localVariable(final SyntaxNode node, final List<FlowSite> found) {
    children(node, found);
    final SyntaxNode type = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), TYPE_TYPE);
    final SyntaxNode variables =
        JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), VARIABLE_DECLARATORS);
    if (variables == null) {
      return;
    }
    for (final SyntaxNode declarator :
        JavaFlowSyntax.allOf(JavaFlowSyntax.parts(variables), VARIABLE_DECLARATOR)) {
      final SyntaxNode id =
          JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(declarator), VARIABLE_DECLARATOR_ID);
      if (id != null) {
        declareType(JavaFlowSyntax.text(JavaSyntaxNames.identifierOf(id)), type);
      }
    }
  }

  private void declareType(final String name, final SyntaxNode type) {
    if (type != null) {
      declared.put(name, JavaFlowSyntax.text(type));
    }
  }

  /** The type of a qualifier: a declared variable's type, or an imported or known type name. */
  private Optional<String> receiverType(final String qualifier) {
    if (qualifier.isEmpty()) {
      return Optional.empty();
    }
    final String written = declared.get(qualifier);
    return written == null ? types.resolveTypeName(qualifier) : types.resolve(written);
  }
}
