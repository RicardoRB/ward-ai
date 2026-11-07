package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Counts the NPath of the branching statements: if, loops, switch and try. */
final class NPathBranches {

  private static final String BLOCK = "block";
  private static final String STATEMENT = "statement";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String CATCH = "catchClause";
  private static final String DEFAULT = "'default'";
  private static final Set<String> LOGICAL = Set.of("'&&'", "'||'");
  private static final long ABSENT_BRANCH = 1L;
  private static final long LOOP_SKIP = 1L;

  private NPathBranches() {}

  /** Returns the NPath of an if: the condition's operators, both branches, and a missing else. */
  static long ifPaths(final SyntaxNode node) {
    final List<SyntaxNode> branches = NPathPaths.children(node, STATEMENT);
    final long otherwise =
        branches.size() > 1 ? NPathPaths.statement(branches.get(1)) : ABSENT_BRANCH;
    return logicalOperators(node) + NPathPaths.statement(branches.get(0)) + otherwise;
  }

  /** Returns the NPath of a loop: its condition, its body, and one path for skipping it. */
  static long loopPaths(final SyntaxNode node) {
    final SyntaxNode body = NPathPaths.children(node, STATEMENT).get(0);
    return logicalOperators(node) + NPathPaths.statement(body) + LOOP_SKIP;
  }

  /** Returns the NPath of a switch: its groups, and one path when there is no default label. */
  static long switchPaths(final SyntaxNode node) {
    long total = 0L;
    boolean hasDefault = false;
    for (final SyntaxNode group : node.children()) {
      if (GROUP.equals(group.kind())) {
        total += groupPaths(group);
        hasDefault |= hasDefaultLabel(group);
      }
    }
    return hasDefault ? total : total + ABSENT_BRANCH;
  }

  /** Returns the NPath of a try: its block and the block of each catch. */
  static long tryPaths(final SyntaxNode node) {
    long total = NPathPaths.block(NPathPaths.child(node, BLOCK));
    for (final SyntaxNode catchClause : NPathPaths.children(node, CATCH)) {
      total += NPathPaths.block(NPathPaths.child(catchClause, BLOCK));
    }
    return total;
  }

  private static boolean hasDefaultLabel(final SyntaxNode group) {
    return group.children().stream()
        .anyMatch(label -> label.children().stream().anyMatch(part -> DEFAULT.equals(part.kind())));
  }

  private static long groupPaths(final SyntaxNode group) {
    long paths = 1L;
    for (final SyntaxNode child : group.children()) {
      if (BLOCK_STATEMENT.equals(child.kind())) {
        paths = NPathPaths.product(paths, NPathPaths.blockStatement(child));
      }
    }
    return paths;
  }

  /** Counts the logical operators of a statement's own parts, not of its nested statements. */
  private static long logicalOperators(final SyntaxNode node) {
    long count = 0L;
    for (final SyntaxNode child : node.children()) {
      if (!STATEMENT.equals(child.kind())) {
        count += countLogical(child);
      }
    }
    return count;
  }

  private static long countLogical(final SyntaxNode node) {
    long count = LOGICAL.contains(node.kind()) ? 1L : 0L;
    for (final SyntaxNode child : node.children()) {
      count += countLogical(child);
    }
    return count;
  }
}
