package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Measures non-commenting source statements (NCSS) with three limits: each method and constructor,
 * each class, and the file. A method counts its own statements and declarations, not the members of
 * an anonymous class inside it. A class counts its own members and their statements, not the nested
 * types inside it. Each counted node also adds one for itself.
 */
public final class NcssCheck implements RuleCheck {

  private static final Set<String> METHODS = Set.of("methodDeclaration", "constructorDeclaration");
  private static final Set<String> TYPES =
      Set.of(
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration",
          "annotationTypeDeclaration");
  private static final Set<String> COUNTED =
      Set.of(
          "statement",
          "localVariableDeclaration",
          "fieldDeclaration",
          "methodDeclaration",
          "constructorDeclaration",
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration",
          "annotationTypeDeclaration");
  private static final Set<String> METHOD_SKIPS = Set.of("classBody", "classDeclaration");
  private static final Set<String> TYPE_SKIPS = TYPES;
  private static final Set<String> NO_SKIPS = Set.of();
  private static final String STATEMENT = "statement";
  private static final String BLOCK = "block";

  private final int methodMax;
  private final int classMax;
  private final int fileMax;
  private final String message;

  /**
   * Creates the check.
   *
   * @param methodMax the largest allowed NCSS of a method or constructor
   * @param classMax the largest allowed NCSS of a class
   * @param fileMax the largest allowed NCSS of a file
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if a limit is negative
   */
  public NcssCheck(
      final int methodMax, final int classMax, final int fileMax, final String message) {
    this.methodMax = CheckSupport.nonNegative(methodMax, "method-maximum");
    this.classMax = CheckSupport.nonNegative(classMax, "class-maximum");
    this.fileMax = CheckSupport.nonNegative(fileMax, "file-maximum");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, METHODS)) {
      report(visit.node(), 1 + count(visit.node(), METHOD_SKIPS), methodMax, matches);
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TYPES)) {
      report(visit.node(), 1 + count(visit.node(), TYPE_SKIPS), classMax, matches);
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("compilationunit"))) {
      report(visit.node(), count(visit.node(), NO_SKIPS), fileMax, matches);
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode node, final int value, final int max, final List<RuleMatch> matches) {
    if (value > max) {
      matches.add(
          new RuleMatch(
              node.span(),
              CheckSupport.fill(message, String.valueOf(value), String.valueOf(max))));
    }
  }

  /** Counts the counted nodes below {@code node}, not entering the subtrees of {@code skips}. */
  private static int count(final SyntaxNode node, final Set<String> skips) {
    int total = 0;
    for (final SyntaxNode child : node.children()) {
      if (counts(child)) {
        total++;
      }
      if (!skips.contains(child.kind())) {
        total += count(child, skips);
      }
    }
    return total;
  }

  /** A statement that only wraps a block is not counted; the statements in the block are. */
  private static boolean counts(final SyntaxNode node) {
    if (!COUNTED.contains(node.kind())) {
      return false;
    }
    return !(STATEMENT.equals(node.kind()) && wrapsBlock(node));
  }

  private static boolean wrapsBlock(final SyntaxNode statement) {
    return statement.children().size() == 1
        && BLOCK.equals(statement.children().get(0).kind());
  }
}
