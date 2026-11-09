package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an empty Kotlin block, detekt's {@code empty-blocks} family. With an {@code after} token
 * the body is the block that follows that token in the owner, such as the one after {@code )} of an
 * {@code if}; without it the body is the owner's own {@code block} or {@code classBody}, or the
 * owner itself when it has neither. A body is empty when it has no code and no comment, so a
 * commented block is not reported, as in detekt.
 */
public final class KotlinEmptyBlockCheck implements RuleCheck {

  private static final String BLOCK = "block";
  private static final String CLASS_BODY = "classBody";
  private static final String BODY = "controlStructureBody";
  private static final String ELSE = "'else'";
  private static final String COMMENT = "comment";
  private static final int SINGLE = 1;
  private static final String FUNCTION_LITERAL = "functionLiteral";
  private static final String END_OF_FILE = "<EOF>";
  private static final Set<String> BRACES = Set.of("{", "}");

  private final Set<String> kinds;
  private final String after;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the owner kinds whose body is checked
   * @param after the token kind before the body, or empty for the owner's own body
   * @param message the message reported at the body
   */
  public KotlinEmptyBlockCheck(final Set<String> kinds, final String after, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.after = Objects.requireNonNull(after, "after");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      bodyOf(visit.node())
          .filter(KotlinEmptyBlockCheck::isEmpty)
          .ifPresent(body -> matches.add(CheckSupport.at(body.span(), message)));
    }
    return List.copyOf(matches);
  }

  private Optional<SyntaxNode> bodyOf(final SyntaxNode owner) {
    if (after.isEmpty()) {
      return Optional.of(ownBody(owner).orElse(owner));
    }
    return bodyAfter(owner);
  }

  private static Optional<SyntaxNode> ownBody(final SyntaxNode owner) {
    return owner.children().stream()
        .filter(child -> BLOCK.equals(child.kind()) || CLASS_BODY.equals(child.kind()))
        .findFirst();
  }

  private Optional<SyntaxNode> bodyAfter(final SyntaxNode owner) {
    boolean seen = false;
    for (final SyntaxNode child : owner.children()) {
      if (seen && ELSE.equals(child.kind())) {
        return Optional.empty();
      }
      if (seen && BLOCK.equals(child.kind())) {
        return Optional.of(child);
      }
      if (seen && BODY.equals(child.kind())) {
        return braceBody(child);
      }
      seen = seen || after.equals(child.kind());
    }
    return Optional.empty();
  }

  /**
   * Follows the single-child chain of a body to its braces. The parser reads {@code { }} after
   * {@code if (...)} as a function literal inside an expression, so that node counts as a block.
   */
  private static Optional<SyntaxNode> braceBody(final SyntaxNode body) {
    SyntaxNode current = body;
    while (!BLOCK.equals(current.kind()) && !FUNCTION_LITERAL.equals(current.kind())) {
      final List<SyntaxNode> significant =
          current.children().stream().filter(c -> !COMMENT.equals(c.kind())).toList();
      if (significant.size() != SINGLE) {
        return Optional.empty();
      }
      current = significant.get(0);
    }
    return Optional.of(current);
  }

  private static boolean isEmpty(final SyntaxNode body) {
    return !hasContent(body);
  }

  private static boolean hasContent(final SyntaxNode node) {
    if (COMMENT.equals(node.kind())) {
      return true;
    }
    if (node.children().isEmpty()) {
      return isContentText(node.fields().getOrDefault("text", ""));
    }
    return node.children().stream().anyMatch(KotlinEmptyBlockCheck::hasContent);
  }

  private static boolean isContentText(final String text) {
    return !text.isBlank() && !BRACES.contains(text) && !END_OF_FILE.equals(text);
  }
}
