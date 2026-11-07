package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each left curly brace that starts a line of its own, so that it ends the line of the
 * token before it. Array initializers are not blocks and are not checked; enum bodies are skipped
 * when {@code ignoreEnums} holds, as Checkstyle does by default.
 */
public final class LeftCurlyCheck implements RuleCheck {

  private static final String OPEN = "'{'";
  private static final Set<String> NOT_BLOCKS =
      Set.of("arrayInitializer", "elementValueArrayInitializer");
  private static final String ENUM = "enumDeclaration";

  private final boolean ignoreEnums;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoreEnums whether the braces of an enum body are exempt
   * @param message the message
   */
  public LeftCurlyCheck(final boolean ignoreEnums, final String message) {
    this.ignoreEnums = ignoreEnums;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final TokenSequence tokens = TokenSequence.of(root);
    for (final TokenSequence.Token token : tokens.all()) {
      if (OPEN.equals(token.node().kind()) && isChecked(token)) {
        final SyntaxNode before = tokens.before(token);
        if (before != null && before.span().endLine() != token.node().span().startLine()) {
          matches.add(CheckSupport.at(token.node().span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private boolean isChecked(final TokenSequence.Token token) {
    final String parent = token.parentKind();
    return !NOT_BLOCKS.contains(parent) && !(ignoreEnums && ENUM.equals(parent));
  }
}
