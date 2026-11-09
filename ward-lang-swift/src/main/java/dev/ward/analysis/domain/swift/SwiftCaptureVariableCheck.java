package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports a name in a closure's capture list that refers to a {@code var} declared outside any
 * function, closure or initializer in the same file, like SwiftLint's {@code capture_variable}. A
 * {@code let} or a local {@code var} is never reported. Simplification: SwiftLint resolves the
 * reference with the compiler, so a name that is also declared as a {@code let} elsewhere is still
 * reported when a {@code var} of that name exists.
 */
public final class SwiftCaptureVariableCheck implements RuleCheck {

  private static final String CAPTURE_ITEM = "capture_list_item";
  private static final String VARIABLE = "variable_declaration";
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String LAZY = "lazy";
  private static final Set<String> LOCAL_SCOPES =
      Set.of("function_body", "initializer_body", "closure_expression", "code_block", "getter_clause");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftCaptureVariableCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> globals = globalVariables(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode item : SwiftTrees.descendants(root, CAPTURE_ITEM)) {
      final List<SyntaxNode> leaves = SwiftTrees.leaves(item);
      if (!leaves.isEmpty() && !hasInitializer(leaves) && globals.contains(nameOf(leaves))) {
        matches.add(SwiftTrees.at(leaves.get(leaves.size() - 1), message));
      }
    }
    return List.copyOf(matches);
  }

  /** The names of the {@code var} declarations that are not local to a function or closure. */
  private static Set<String> globalVariables(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    collectGlobals(root, false, names);
    return names;
  }

  private static void collectGlobals(final SyntaxNode node, final boolean local, final Set<String> names) {
    if (VARIABLE.equals(node.kind()) && !local && !isLazy(node)) {
      SwiftTrees.descendants(node, IDENTIFIER).stream()
          .findFirst()
          .map(SwiftTrees::compact)
          .ifPresent(names::add);
    }
    final boolean nested = local || LOCAL_SCOPES.contains(node.kind());
    node.children().forEach(child -> collectGlobals(child, nested, names));
  }

  private static boolean isLazy(final SyntaxNode variable) {
    return SwiftTrees.hasToken(variable, LAZY);
  }

  private static boolean hasInitializer(final List<SyntaxNode> leaves) {
    return leaves.stream().anyMatch(leaf -> "=".equals(leaf.fields().get("text")));
  }

  /** The captured name: the last identifier of the item, so {@code weak x} gives {@code x}. */
  private static String nameOf(final List<SyntaxNode> leaves) {
    return leaves.get(leaves.size() - 1).fields().get("text");
  }
}
