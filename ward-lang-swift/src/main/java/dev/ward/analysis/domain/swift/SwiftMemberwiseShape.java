package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Reads the parts of a struct that its memberwise initializer is made of: the stored properties
 * without a default value, and the parameters and assignments of an initializer.
 */
final class SwiftMemberwiseShape {

  private static final String STRUCT_BODY = "struct_body";
  private static final String MEMBER = "struct_member";
  private static final String DECLARATION = "declaration";
  private static final String VARIABLE = "variable_declaration";
  private static final String CONSTANT = "constant_declaration";
  private static final String CODE_BLOCK = "code_block";
  private static final String TYPE_ANNOTATION = "type_annotation";
  private static final String TYPE = "type_";
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String LOCAL_NAME = "local_parameter_name";
  private static final String PARAMETER = "parameter";
  private static final String PARAMETER_CLAUSE = "parameter_clause";

  private SwiftMemberwiseShape() {}

  /** The members declared directly in the struct's body, not those of nested types. */
  static List<SyntaxNode> members(final SyntaxNode struct) {
    return SwiftTrees.child(struct, STRUCT_BODY)
        .map(body -> body.children().stream().filter(child -> MEMBER.equals(child.kind())).toList())
        .orElse(List.of());
  }

  /** The stored properties, as name and type text, in declaration order. */
  static List<String[]> storedProperties(final SyntaxNode struct) {
    return bindings(struct).stream()
        .filter(SwiftMemberwiseShape::isStoredWithoutDefault)
        .map(binding -> new String[] {nameOf(binding), typeOf(binding)})
        .toList();
  }

  /** The parameters of an initializer, in order. */
  static List<SyntaxNode> parameters(final SyntaxNode init) {
    return SwiftTrees.child(init, PARAMETER_CLAUSE)
        .map(clause -> SwiftTrees.descendants(clause, PARAMETER))
        .orElse(List.of());
  }

  /** The internal name of a parameter, which is the name it forwards to the property. */
  static String localNameOf(final SyntaxNode parameter) {
    return SwiftTrees.child(parameter, LOCAL_NAME).map(SwiftTrees::compact).orElse("");
  }

  /** The declared type of a parameter or binding, as text. */
  static String typeOf(final SyntaxNode node) {
    return SwiftTrees.descendants(node, TYPE_ANNOTATION).stream()
        .findFirst()
        .flatMap(annotation -> SwiftTrees.child(annotation, TYPE))
        .map(SwiftTrees::compact)
        .orElse("");
  }

  private static List<SyntaxNode> bindings(final SyntaxNode struct) {
    return members(struct).stream()
        .flatMap(member -> member.children().stream())
        .filter(child -> DECLARATION.equals(child.kind()))
        .flatMap(declaration -> declaration.children().stream())
        .filter(binding -> VARIABLE.equals(binding.kind()) || CONSTANT.equals(binding.kind()))
        .toList();
  }

  private static boolean isStoredWithoutDefault(final SyntaxNode binding) {
    final boolean computed =
        binding.children().stream().anyMatch(child -> CODE_BLOCK.equals(child.kind()));
    return !computed && !SwiftTrees.hasToken(binding, "=");
  }

  private static String nameOf(final SyntaxNode binding) {
    return SwiftTrees.descendants(binding, IDENTIFIER).stream()
        .findFirst()
        .map(SwiftTrees::compact)
        .orElse("");
  }
}
