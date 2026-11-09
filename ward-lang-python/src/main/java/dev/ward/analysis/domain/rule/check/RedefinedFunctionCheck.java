package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a function or class that redefines a name of an earlier function or class in the same
 * scope, whether or not the earlier one is used, like Pylint's {@code function-redefined}. Exempt
 * are a redefinition under a condition or try (its branches are exclusive), a property setter or
 * deleter ({@code @name.setter}), and an {@code @overload} stub. Shortcut: the condition test is
 * the enclosing statement kind, not the branch structure.
 */
public final class RedefinedFunctionCheck implements RuleCheck {

  private static final Set<BindingKind> DEFINITIONS =
      Set.of(BindingKind.FUNCTION, BindingKind.CLASS);
  private static final Set<String> CONDITIONAL =
      Set.of("if_stmt", "try_stmt", "while_stmt", "for_stmt", "with_stmt");
  private static final Set<String> DEFINITION_NODES = Set.of("funcdef", "classdef");
  private static final String MESSAGE_NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public RedefinedFunctionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the function-redefined check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : view.scopes().scopes()) {
      addRedefinitions(root, scope.bindings(), found);
    }
    found.sort(java.util.Comparator.comparingInt(match -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private void addRedefinitions(
      final SyntaxNode root, final List<Binding> bindings, final List<RuleMatch> found) {
    for (int index = 0; index < bindings.size(); index++) {
      final Binding current = bindings.get(index);
      if (!DEFINITIONS.contains(current.kind())) {
        continue;
      }
      final boolean earlier =
          bindings.subList(0, index).stream()
              .anyMatch(b -> DEFINITIONS.contains(b.kind()) && b.name().equals(current.name()));
      if (earlier) {
        final Definition definition = definitionAt(root, current);
        if (definition != null && !exempt(definition, current.name())) {
          found.add(
              new RuleMatch(
                  definition.node().span(), message.replace(MESSAGE_NAME, current.name())));
        }
      }
    }
  }

  /** A definition node in the tree with the list of its ancestors, nearest last. */
  private record Definition(SyntaxNode node, List<SyntaxNode> ancestors) {}

  private static Definition definitionAt(final SyntaxNode root, final Binding binding) {
    final int offset = binding.declaration().startOffset();
    return find(root, offset, new ArrayList<>());
  }

  /** The innermost definition that contains the offset, with the nodes above it. */
  private static Definition find(
      final SyntaxNode node, final int offset, final List<SyntaxNode> ancestors) {
    for (final SyntaxNode child : node.children()) {
      if (contains(child, offset)) {
        ancestors.add(node);
        final Definition inside = find(child, offset, ancestors);
        ancestors.remove(ancestors.size() - 1);
        if (inside != null) {
          return inside;
        }
        break;
      }
    }
    if (DEFINITION_NODES.contains(node.kind()) && contains(node, offset)) {
      return new Definition(node, List.copyOf(ancestors));
    }
    return null;
  }

  private static boolean contains(final SyntaxNode node, final int offset) {
    return node.span().startOffset() <= offset && offset < node.span().endOffset();
  }

  /** Whether the redefinition is exempt: conditional, a property accessor, or an overload. */
  private static boolean exempt(final Definition definition, final String name) {
    final List<SyntaxNode> ancestors = definition.ancestors();
    for (final SyntaxNode ancestor : ancestors) {
      if (CONDITIONAL.contains(ancestor.kind())) {
        return true;
      }
      if ("decorated".equals(ancestor.kind()) && decoratedExempt(ancestor, name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean decoratedExempt(final SyntaxNode decorated, final String name) {
    for (final SyntaxNode child : decorated.children()) {
      if ("funcdef".equals(child.kind()) || "classdef".equals(child.kind())) {
        continue;
      }
      final String text = SyntaxWalk.text(child);
      if (text.startsWith("@" + name + ".") || text.contains("overload")) {
        return true;
      }
    }
    return false;
  }
}
