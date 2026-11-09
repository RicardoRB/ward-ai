package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports an import that repeats an earlier import of the same module path, and an import of a
 * submodule of another imported module, like SwiftLint's {@code duplicate_imports}. An import with
 * attributes is never a partial duplicate. Imports inside conditional compilation are not compared
 * with each other, which is a simplification of SwiftLint's rule.
 */
public final class SwiftDuplicateImportsCheck implements RuleCheck {

  private static final String IMPORT = "import_declaration";
  private static final String PATH = "import_path";
  private static final String CONDITIONAL = "conditional_compilation_block";
  private static final String DOT = ".";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftDuplicateImportsCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> imports = new ArrayList<>();
    final List<Boolean> conditional = new ArrayList<>();
    collect(root, false, imports, conditional);
    final List<RuleMatch> matches = new ArrayList<>();
    final Set<String> seen = new HashSet<>();
    for (int i = 0; i < imports.size(); i++) {
      final SyntaxNode node = imports.get(i);
      final String path = pathOf(node);
      if (!conditional.get(i) && !seen.add(path)) {
        matches.add(SwiftTrees.at(node, message));
      } else if (!hasAttributes(node) && isSubmoduleOfOther(node, path, imports)) {
        matches.add(SwiftTrees.at(node, message));
      }
    }
    return List.copyOf(matches);
  }

  private static void collect(
      final SyntaxNode node,
      final boolean inConditional,
      final List<SyntaxNode> imports,
      final List<Boolean> conditional) {
    final boolean nested = inConditional || CONDITIONAL.equals(node.kind());
    if (IMPORT.equals(node.kind())) {
      imports.add(node);
      conditional.add(nested);
    }
    node.children().forEach(child -> collect(child, nested, imports, conditional));
  }

  private static String pathOf(final SyntaxNode node) {
    return SwiftTrees.child(node, PATH).map(SwiftTrees::compact).orElse("");
  }

  private static boolean hasAttributes(final SyntaxNode node) {
    return SwiftTrees.compact(node).startsWith("@");
  }

  private static boolean isSubmoduleOfOther(
      final SyntaxNode node, final String path, final List<SyntaxNode> imports) {
    return imports.stream()
        .filter(other -> other != node)
        .map(SwiftDuplicateImportsCheck::pathOf)
        .anyMatch(other -> !other.equals(path) && path.startsWith(other + DOT));
  }
}
