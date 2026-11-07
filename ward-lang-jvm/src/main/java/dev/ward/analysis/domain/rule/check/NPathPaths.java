package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Counts the NPath of a method body: the acyclic execution paths through its statements. */
final class NPathPaths {

  private static final long CAP = 1_000_000_000L;
  private static final String BLOCK = "block";
  private static final String STATEMENT = "statement";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String IF = "'if'";
  private static final String SWITCH = "'switch'";
  private static final String TRY = "'try'";
  private static final Set<String> LOOPS = Set.of("'while'", "'for'", "'do'");

  private NPathPaths() {}

  /** Returns the NPath of a block: the product of its statements. */
  static long block(final SyntaxNode block) {
    long paths = 1L;
    for (final SyntaxNode child : block.children()) {
      if (BLOCK_STATEMENT.equals(child.kind())) {
        paths = product(paths, blockStatement(child));
      }
    }
    return paths;
  }

  /** Returns the NPath of a statement. */
  static long statement(final SyntaxNode node) {
    final Set<String> kinds = kindsOf(node);
    if (kinds.contains(IF)) {
      return NPathBranches.ifPaths(node);
    }
    if (kinds.stream().anyMatch(LOOPS::contains)) {
      return NPathBranches.loopPaths(node);
    }
    if (kinds.contains(SWITCH)) {
      return NPathBranches.switchPaths(node);
    }
    if (kinds.contains(TRY)) {
      return NPathBranches.tryPaths(node);
    }
    return kinds.contains(BLOCK) ? block(child(node, BLOCK)) : 1L;
  }

  /** Returns the NPath of a block statement, which is one path unless it holds a statement. */
  static long blockStatement(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> STATEMENT.equals(child.kind()))
        .findFirst()
        .map(NPathPaths::statement)
        .orElse(1L);
  }

  static long product(final long left, final long right) {
    return Math.min(left * right, CAP);
  }

  static SyntaxNode child(final SyntaxNode node, final String kind) {
    return children(node, kind).get(0);
  }

  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  private static Set<String> kindsOf(final SyntaxNode node) {
    return node.children().stream().map(SyntaxNode::kind).collect(Collectors.toUnmodifiableSet());
  }
}
