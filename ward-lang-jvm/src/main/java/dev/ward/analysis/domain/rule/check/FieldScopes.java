package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the class members around a syntax node: its class body, static context and setters. */
final class FieldScopes {

  private static final Set<String> CONTAINERS = Set.of("classBody", "enumBodyDeclarations");
  private static final Set<String> NOT_ENTERED = Set.of("classBody", "block");
  private static final Set<String> OPENERS = Set.of("(", "{", "=");
  private static final String MEMBER = "classBodyDeclaration";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String METHOD = "methodDeclaration";
  private static final String STATIC = "static";
  private static final String VOID = "void";
  private static final String SETTER = "set";
  private static final String OPEN = "(";
  private static final int FIRST_INDEX = 1;
  private static final int NOT_FOUND = -1;

  private FieldScopes() {}

  /** Returns the innermost class body among {@code ancestors}, or null when there is none. */
  static SyntaxNode container(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CONTAINERS.contains(ancestors.get(index).kind())) {
        return ancestors.get(index);
      }
    }
    return null;
  }

  /** Whether the innermost class member around these ancestors is static. */
  static boolean staticContext(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (MEMBER.equals(ancestors.get(index).kind())) {
        return staticBeforeOpener(ancestors.get(index));
      }
    }
    return false;
  }

  private static boolean staticBeforeOpener(final SyntaxNode member) {
    for (final SyntaxNode leaf : SyntaxWalk.leaves(member)) {
      final String text = SyntaxWalk.text(leaf);
      if (OPENERS.contains(text)) {
        return false;
      }
      if (STATIC.equals(text)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the variable declarators below {@code node}, not entering blocks or class bodies. */
  static List<SyntaxNode> declarators(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (DECLARATOR.equals(child.kind())) {
        found.add(child);
      } else if (!NOT_ENTERED.contains(child.kind())) {
        found.addAll(declarators(child));
      }
    }
    return found;
  }

  /** Returns the first token below {@code node}. */
  static SyntaxNode firstLeaf(final SyntaxNode node) {
    return SyntaxWalk.leaves(node).get(0);
  }

  /** Whether {@code method} is a void setter for a field named {@code parameter}. */
  static boolean isSetterOf(final SyntaxNode method, final String parameter) {
    if (!METHOD.equals(method.kind())) {
      return false;
    }
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(method);
    final int open = indexOf(leaves, OPEN);
    if (open < FIRST_INDEX) {
      return false;
    }
    final String methodName = SyntaxWalk.text(leaves.get(open - 1));
    final boolean returnsVoid =
        leaves.subList(0, open - 1).stream().anyMatch(leaf -> VOID.equals(SyntaxWalk.text(leaf)));
    final String expected =
        SETTER + Character.toUpperCase(parameter.charAt(0)) + parameter.substring(1);
    return returnsVoid && methodName.equals(expected);
  }

  private static int indexOf(final List<SyntaxNode> leaves, final String text) {
    for (int index = 0; index < leaves.size(); index++) {
      if (text.equals(SyntaxWalk.text(leaves.get(index)))) {
        return index;
      }
    }
    return NOT_FOUND;
  }
}
