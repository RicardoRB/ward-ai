package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** The declarations of a source file with the syntax they came from. */
final class SourceModel {
  private SourceModel() {}

  /** Returns a variable with no known type. */
  static Variable untyped(final SyntaxNode identifier) {
    return new Variable(identifier, Optional.empty());
  }

  /**
   * A variable the declaration introduces.
   *
   * @param identifier the identifier node, whose start offset keys the variable in scopes
   * @param type the declared type, empty when it did not resolve
   */
  record Variable(SyntaxNode identifier, Optional<JType> type) {}

  /**
   * A member of a class.
   *
   * @param node the declaration node
   * @param outer the node whose span includes the modifiers
   * @param scope the scope inside the member, with its type variables
   * @param method the method or constructor, empty for a field
   * @param field the fields a field declaration introduces
   * @param variables the parameters and fields to register
   * @param body the body block or initializer, if any
   */
  record Member(
      SyntaxNode node,
      SyntaxNode outer,
      TypeScope scope,
      Optional<JvmMethod> method,
      List<JvmField> field,
      List<Variable> variables,
      Optional<SyntaxNode> body) {}

  /**
   * A class with the syntax of its declaration.
   *
   * @param jvm the class as other files see it
   * @param node the declaration node
   * @param outer the node whose span includes the modifiers
   * @param scope the scope inside the class
   * @param members the members, in source order
   * @param nested the member classes
   */
  record ClassDecl(
      JvmClass jvm,
      SyntaxNode node,
      SyntaxNode outer,
      TypeScope scope,
      List<Member> members,
      List<ClassDecl> nested) {}
}
