package dev.ward.analysis.domain.rule.check;

import java.util.Set;

/** The node kinds that open a body of their own, so a walk over one body stops at them. */
final class NodeKinds {

  /** Nested functions, accessors, methods and classes: their returns belong to them. */
  static final Set<String> NESTED_BODIES =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "arrowFunctionDeclaration",
          "methodDefinition",
          "propertyAssignment",
          "propertyMemberDeclaration",
          "constructorDeclaration",
          "getAccessor",
          "setAccessor",
          "classDeclaration");

  private NodeKinds() {}
}
