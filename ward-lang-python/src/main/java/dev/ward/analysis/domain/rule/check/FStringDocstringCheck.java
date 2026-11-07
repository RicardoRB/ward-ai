package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an f-string that is the first statement of a function or class body, where Python does
 * not read it as a docstring, like pyflakes-style {@code B021}. The finding sits on the string.
 */
public final class FStringDocstringCheck implements RuleCheck {

  private static final Set<String> SCOPES = Set.of("funcdef", "classdef");
  private static final String BLOCK = "block";
  private static final Pattern FSTRING = Pattern.compile("^[rRbBuU]{0,2}[fF][rRbBuU]?[\"'].*");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public FStringDocstringCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, SCOPES)) {
      firstLeaf(visit.node())
          .filter(leaf -> FSTRING.matcher(SyntaxWalk.text(leaf)).matches())
          .ifPresent(leaf -> found.add(new RuleMatch(leaf.span(), message)));
    }
    return List.copyOf(found);
  }

  /** The first token of the body, skipping the newline and indentation before it. */
  private static Optional<SyntaxNode> firstLeaf(final SyntaxNode scope) {
    for (final SyntaxNode child : scope.children()) {
      if (BLOCK.equals(child.kind())) {
        return SyntaxWalk.leaves(child).stream()
            .filter(leaf -> !SyntaxWalk.text(leaf).isBlank())
            .findFirst();
      }
    }
    return Optional.empty();
  }
}
