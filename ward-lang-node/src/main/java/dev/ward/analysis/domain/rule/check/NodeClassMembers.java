package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The class members of the tree that the hidden-field check compares with local bindings. */
final class NodeClassMembers {

  private static final String CLASS_TAIL = "classTail";
  private static final String ELEMENT = "classElement";
  private static final String CALL_SIGNATURE = "callSignature";
  private static final String PARAMETER_LIST = "parameterList";
  private static final String FORMAL_PARAMETERS = "formalParameterList";
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final String VOID = "void";
  private static final Set<String> MODIFIERS = Set.of("public", "private", "protected", "readonly");

  private NodeClassMembers() {}

  /** The class elements of a class declaration, in source order. */
  static List<SyntaxNode> elements(final SyntaxNode declared) {
    final List<SyntaxNode> members = new ArrayList<>();
    for (final SyntaxNode part : NodeSyntax.code(declared)) {
      if (CLASS_TAIL.equals(part.kind())) {
        NodeSyntax.code(part).stream()
            .filter(element -> ELEMENT.equals(element.kind()))
            .forEach(members::add);
      }
    }
    return members;
  }

  /** The names of the fields: field declarations, and the parameter properties of constructors. */
  static Set<String> fieldNames(final List<SyntaxNode> members) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode element : members) {
      if (isField(element)) {
        NodeSyntax.firstIdentifier(element).ifPresent(name -> names.add(NodeSyntax.text(name)));
      }
      if (isConstructor(element)) {
        parameterProperties(element).forEach(names::add);
      }
    }
    return names;
  }

  static boolean isConstructor(final SyntaxNode element) {
    return NodeSyntax.code(element).stream()
            .anyMatch(part -> "constructorDeclaration".equals(part.kind()))
        || NodeSyntax.code(element).stream()
            .anyMatch(
                part ->
                    "methodDefinition".equals(part.kind())
                        && "constructor"
                            .equals(NodeSyntax.leaves(part).stream().findFirst().orElse("")));
  }

  /** The parameter nodes of a function or constructor element: children of its parameter list. */
  static List<SyntaxNode> allParameters(final SyntaxNode element) {
    final List<SyntaxNode> parameters = new ArrayList<>();
    for (final SyntaxNode part : element.children()) {
      if (FORMAL_PARAMETERS.equals(part.kind()) || PARAMETER_LIST.equals(part.kind())) {
        NodeSyntax.code(part).stream()
            .filter(child -> !child.children().isEmpty())
            .forEach(parameters::add);
      }
      parameters.addAll(allParameters(part));
    }
    return parameters;
  }

  /** Whether the binding's declaration is the parameter property of the constructor. */
  static boolean isParameterProperty(final SyntaxNode element, final int declarationStart) {
    return isConstructor(element)
        && allParameters(element).stream()
            .filter(parameter -> isModifier(NodeSyntax.leaves(parameter).get(0)))
            .flatMap(parameter -> NodeSyntax.firstIdentifier(parameter).stream())
            .anyMatch(name -> name.span().startOffset() == declarationStart);
  }

  /**
   * A setter for the field: a method named set plus the field name, one parameter, no value
   * returned.
   */
  static boolean isSetter(final SyntaxNode element, final String field) {
    if (!isMethod(element) || isConstructor(element)) {
      return false;
    }
    final String name = NodeSyntax.firstIdentifier(element).map(NodeSyntax::text).orElse("");
    return name.equals("set" + Character.toUpperCase(field.charAt(0)) + field.substring(1))
        && allParameters(element).size() == 1
        && !returnsValue(element);
  }

  private static boolean isField(final SyntaxNode element) {
    return !isConstructor(element) && !isMethod(element);
  }

  private static boolean isMethod(final SyntaxNode element) {
    return NodeSyntax.code(element).stream().anyMatch(part -> hasChild(part, CALL_SIGNATURE))
        || NodeSyntax.code(element).stream()
            .anyMatch(part -> "methodDefinition".equals(part.kind()));
  }

  private static boolean hasChild(final SyntaxNode node, final String kind) {
    return node.children().stream().anyMatch(child -> kind.equals(child.kind()));
  }

  /**
   * The parameter properties of a constructor: parameters with an accessibility modifier or
   * readonly.
   */
  private static List<String> parameterProperties(final SyntaxNode constructor) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode parameter : allParameters(constructor)) {
      if (isModifier(NodeSyntax.leaves(parameter).get(0))) {
        NodeSyntax.firstIdentifier(parameter).ifPresent(name -> names.add(NodeSyntax.text(name)));
      }
    }
    return names;
  }

  private static boolean isModifier(final String text) {
    return MODIFIERS.contains(text);
  }

  private static boolean returnsValue(final SyntaxNode element) {
    final Optional<SyntaxNode> call =
        NodeSyntax.code(element).stream()
            .filter(part -> CALL_SIGNATURE.equals(part.kind()))
            .findFirst();
    return call.isPresent()
        && call.get().children().stream()
            .filter(part -> TYPE_ANNOTATION.equals(part.kind()))
            .anyMatch(part -> !VOID.equals(NodeSyntax.text(part).replace(":", "")));
  }
}
