package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a private class member that is declared but never read, like ESLint {@code
 * no-unused-private-class-members}. A write alone does not count: a plain assignment or an
 * increment of {@code this.#x} leaves the member unused. Each class body is its own scope.
 */
public final class NodeUnusedPrivateMembersCheck implements RuleCheck {

  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CLASS_EXPRESSION = "classExpression";
  private static final String PRIVATE = "privateIdentifier";
  private static final String ACCESS = "singleExpression";
  private static final String CLASS_ELEMENT = "classElement";
  private static final String ASSIGN = "=";
  private static final Set<String> UPDATES = Set.of("++", "--");
  private static final String HASH = "#";
  private static final int ASSIGN_PARTS = 3;
  private static final int UPDATE_PARTS = 2;
  private static final String MESSAGE = "'%s' is defined but never used";

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collectClasses(root, matches);
    return List.copyOf(matches);
  }

  private void collectClasses(final SyntaxNode node, final List<RuleMatch> matches) {
    if (CLASS_DECLARATION.equals(node.kind()) || CLASS_EXPRESSION.equals(node.kind())) {
      unusedIn(node, matches);
    }
    for (final SyntaxNode child : node.children()) {
      collectClasses(child, matches);
    }
  }

  /** The members a class declares and does not read. */
  private void unusedIn(final SyntaxNode classNode, final List<RuleMatch> matches) {
    final Map<String, SyntaxNode> declared = new HashMap<>();
    final Set<String> read = new HashSet<>();
    scan(classNode, new ArrayList<>(), declared, read);
    for (final Map.Entry<String, SyntaxNode> entry : declared.entrySet()) {
      if (!read.contains(entry.getKey())) {
        matches.add(
            new RuleMatch(entry.getValue().span(), String.format(MESSAGE, HASH + entry.getKey())));
      }
    }
  }

  private void scan(
      final SyntaxNode node,
      final List<SyntaxNode> ancestors,
      final Map<String, SyntaxNode> declared,
      final Set<String> read) {
    ancestors.add(node);
    if (PRIVATE.equals(node.kind())) {
      classify(node, ancestors, declared, read);
    }
    if (ACCESS.equals(node.kind())) {
      accessName(node).ifPresent(name -> readUnlessWritten(name, node, ancestors, read));
    }
    for (final SyntaxNode child : node.children()) {
      scan(child, ancestors, declared, read);
    }
    ancestors.remove(ancestors.size() - 1);
  }

  /**
   * A name outside any expression is a declaration; inside one, it is a read unless written only.
   */
  private static void classify(
      final SyntaxNode node,
      final List<SyntaxNode> ancestors,
      final Map<String, SyntaxNode> declared,
      final Set<String> read) {
    final String name = LeafText.of(node).replace(HASH, "");
    final Optional<SyntaxNode> access = nearestAccess(ancestors);
    if (access.isEmpty()) {
      declared.putIfAbsent(name, node);
    } else {
      readUnlessWritten(name, access.get(), ancestors, read);
    }
  }

  private static void readUnlessWritten(
      final String name,
      final SyntaxNode access,
      final List<SyntaxNode> ancestors,
      final Set<String> read) {
    if (!isWriteOnly(access, ancestors)) {
      read.add(name);
    }
  }

  /** The name of a member access written {@code obj.#name}, whose hash is a direct child. */
  private static Optional<String> accessName(final SyntaxNode access) {
    final List<SyntaxNode> children = access.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      final SyntaxNode child = children.get(index);
      if (child.children().isEmpty() && HASH.equals(LeafText.of(child))) {
        return Optional.of(LeafText.of(children.get(index + 1)));
      }
    }
    return Optional.empty();
  }

  /** The nearest enclosing member access of a class element's expression, if one exists. */
  private static Optional<SyntaxNode> nearestAccess(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 2; index >= 0; index--) {
      final SyntaxNode ancestor = ancestors.get(index);
      if (CLASS_ELEMENT.equals(ancestor.kind())) {
        return Optional.empty();
      }
      if (ACCESS.equals(ancestor.kind())) {
        return Optional.of(ancestor);
      }
    }
    return Optional.empty();
  }

  /**
   * The access is written only: the target of a plain assignment or of an increment or decrement.
   */
  private static boolean isWriteOnly(final SyntaxNode access, final List<SyntaxNode> ancestors) {
    final int index = ancestors.lastIndexOf(access);
    if (index <= 0 || !ACCESS.equals(ancestors.get(index - 1).kind())) {
      return false;
    }
    final List<SyntaxNode> parts = ancestors.get(index - 1).children();
    if (parts.size() == ASSIGN_PARTS) {
      return access.equals(parts.get(0)) && ASSIGN.equals(LeafText.of(parts.get(1)));
    }
    if (parts.size() == UPDATE_PARTS) {
      return access.equals(parts.get(0)) && UPDATES.contains(LeafText.of(parts.get(1)))
          || access.equals(parts.get(1)) && UPDATES.contains(LeafText.of(parts.get(0)));
    }
    return false;
  }
}
