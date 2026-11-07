package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/**
 * One step of the operand chain that follows the leading name of a misshapen expression.
 *
 * @param kind what the step is
 * @param op the operator token, empty for calls and indexes
 * @param nodes the nodes the step applies
 */
record ChainItem(Kind kind, String op, List<SyntaxNode> nodes) {

  static final Map<String, Integer> PRECEDENCE =
      Map.ofEntries(
          Map.entry("*", 13),
          Map.entry("/", 13),
          Map.entry("%", 13),
          Map.entry("+", 12),
          Map.entry("-", 12),
          Map.entry("<", 10),
          Map.entry(">", 10),
          Map.entry("<=", 10),
          Map.entry(">=", 10),
          Map.entry("instanceof", 10),
          Map.entry("in", 10),
          Map.entry("==", 9),
          Map.entry("!=", 9),
          Map.entry("===", 9),
          Map.entry("!==", 9),
          Map.entry("&", 8),
          Map.entry("^", 7),
          Map.entry("|", 6),
          Map.entry("&&", 5),
          Map.entry("||", 4),
          Map.entry("??", 3));

  /** The kinds of step. */
  enum Kind {
    /** A binary operator and its right operand. */
    BINARY,
    /** A call. */
    CALL,
    /** A member access. */
    MEMBER,
    /** An index. */
    INDEX,
    /** A non-null assertion. */
    NONNULL
  }
}
