package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores each method and constructor with its NPath complexity: the number of acyclic execution
 * paths through its body. Statements in sequence multiply, an {@code if} adds its branches and the
 * logical operators of its condition, a loop adds its body, its condition and one path for skipping
 * it, a {@code switch} adds its cases, and a {@code try} adds its block and its catch blocks. A
 * method fails when its score is above the limit.
 */
public final class NPathCheck implements RuleCheck {

  private static final Set<String> SCOPES = Set.of("methodDeclaration", "constructorDeclaration");
  private static final long CAP = 1_000_000_000L;
  private static final String BLOCK = "block";
  private static final String STATEMENT = "statement";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final Set<String> LOOPS = Set.of("'while'", "'for'", "'do'");
  private static final Set<String> LOGICAL = Set.of("'&&'", "'||'");

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed NPath score
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public NPathCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, SCOPES)) {
      final SyntaxNode node = visit.node();
      final long score = body(node).map(NPathCheck::block).orElse(1L);
      if (score > max) {
        matches.add(
            new RuleMatch(
                node.span(),
                CheckSupport.fill(message, String.valueOf(score), String.valueOf(max))));
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<SyntaxNode> body(final SyntaxNode scope) {
    for (final SyntaxNode child : scope.children()) {
      if ("methodBody".equals(child.kind())) {
        return child.children().stream().filter(part -> BLOCK.equals(part.kind())).findFirst();
      }
      if (BLOCK.equals(child.kind())) {
        return Optional.of(child);
      }
    }
    return Optional.empty();
  }

  private static long block(final SyntaxNode block) {
    long paths = 1L;
    for (final SyntaxNode child : block.children()) {
      if (BLOCK_STATEMENT.equals(child.kind())) {
        paths = product(paths, blockStatement(child));
      }
    }
    return paths;
  }

  private static long blockStatement(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> STATEMENT.equals(child.kind()))
        .findFirst()
        .map(NPathCheck::statement)
        .orElse(1L);
  }

  private static long statement(final SyntaxNode node) {
    final Set<String> kinds = kindsOf(node);
    if (kinds.contains("'if'")) {
      return ifPaths(node);
    }
    if (kinds.stream().anyMatch(LOOPS::contains)) {
      return loopPaths(node);
    }
    if (kinds.contains("'switch'")) {
      return switchPaths(node);
    }
    if (kinds.contains("'try'")) {
      return tryPaths(node);
    }
    return kinds.contains(BLOCK) ? block(child(node, BLOCK)) : 1L;
  }

  private static long ifPaths(final SyntaxNode node) {
    final List<SyntaxNode> branches = children(node, STATEMENT);
    final long otherwise = branches.size() > 1 ? statement(branches.get(1)) : 1L;
    return logicalOperators(node) + statement(branches.get(0)) + otherwise;
  }

  private static long loopPaths(final SyntaxNode node) {
    final SyntaxNode body = children(node, STATEMENT).get(0);
    return logicalOperators(node) + statement(body) + 1L;
  }

  private static long switchPaths(final SyntaxNode node) {
    long total = 0L;
    boolean hasDefault = false;
    for (final SyntaxNode group : node.children()) {
      if ("switchBlockStatementGroup".equals(group.kind())) {
        total += groupPaths(group);
        hasDefault |= group.children().stream().anyMatch(label -> label.children().stream()
            .anyMatch(part -> "'default'".equals(part.kind())));
      }
    }
    return hasDefault ? total : total + 1L;
  }

  private static long groupPaths(final SyntaxNode group) {
    long paths = 1L;
    for (final SyntaxNode child : group.children()) {
      if (BLOCK_STATEMENT.equals(child.kind())) {
        paths = product(paths, blockStatement(child));
      }
    }
    return paths;
  }

  private static long tryPaths(final SyntaxNode node) {
    long total = block(child(node, BLOCK));
    final List<SyntaxNode> catches = children(node, "catchClause");
    for (final SyntaxNode catchClause : catches) {
      total += block(child(catchClause, BLOCK));
    }
    return total;
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

  private static long product(final long left, final long right) {
    return Math.min(left * right, CAP);
  }

  private static Set<String> kindsOf(final SyntaxNode node) {
    return node.children().stream().map(SyntaxNode::kind).collect(Collectors.toUnmodifiableSet());
  }

  private static SyntaxNode child(final SyntaxNode node, final String kind) {
    return children(node, kind).get(0);
  }

  private static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }
}
