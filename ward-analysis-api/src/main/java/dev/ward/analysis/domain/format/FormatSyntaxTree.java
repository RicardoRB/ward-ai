package dev.ward.analysis.domain.format;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * What the safety checks need from a parse: the first syntax error line, every non-whitespace token
 * in order (comments included), and the parse-tree rule kinds in pre-order.
 *
 * @param errorLine the line of the first syntax error, or empty when the text parses cleanly
 * @param tokens the non-whitespace tokens in source order, copied on construction
 * @param rules the rule kinds in pre-order, copied on construction
 */
public record FormatSyntaxTree(
    OptionalInt errorLine, List<FormatToken> tokens, List<FormatRule> rules) {

  /**
   * Copies the token and rule lists.
   *
   * @throws NullPointerException if a component is missing
   */
  public FormatSyntaxTree {
    Objects.requireNonNull(errorLine, "errorLine");
    tokens = List.copyOf(tokens);
    rules = List.copyOf(rules);
  }
}
