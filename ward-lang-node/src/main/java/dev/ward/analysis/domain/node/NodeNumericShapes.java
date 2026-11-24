package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The expressions whose value is a number or a boolean whatever their operands are: arithmetic,
 * comparison, a unary operator, a numeric conversion, a numeric member.
 */
final class NodeNumericShapes {
  private static final Pattern NUMERIC_CALL =
      Pattern.compile("(?:Number|parseInt|parseFloat|Boolean|Math\\.\\w+|Date\\.now)");
  private static final Pattern NUMERIC_METHOD =
      Pattern.compile(".*\\.(?:toFixed|toPrecision|toExponential)");
  private static final Set<String> MEMBERS = Set.of("length", "byteLength", "size");
  private static final Set<String> OPERATORS =
      Set.of(
          "*",
          "/",
          "%",
          "**",
          "-",
          "<",
          ">",
          "<=",
          ">=",
          "==",
          "!=",
          "===",
          "!==",
          "instanceof"
      );
  private static final Set<String> UNARY = Set.of("+", "-", "~", "!", "typeof", "void");
  private static final Set<String> ASSIGNMENTS =
      Set.of("-=", "*=", "/=", "%=", "**=", "&=", "|=", "^=", "<<=", ">>=", ">>>=");
  private static final String ARGUMENTS = "arguments";

  private NodeNumericShapes() {}

  /** Whether the binary operator gives a number or a boolean. */
  static boolean operator(final String operator) {
    return OPERATORS.contains(operator);
  }

  /** Whether the compound assignment operator gives a number. */
  static boolean assignment(final String operator) {
    return ASSIGNMENTS.contains(operator);
  }

  /** Whether the member of an object is a count. */
  static boolean member(final String name) {
    return MEMBERS.contains(name);
  }

  /** A unary operator, {@code Number(x)}, {@code Math.max(a, b)} or {@code n.toFixed(2)}. */
  static boolean unaryOrCall(final List<SyntaxNode> parts) {
    if (UNARY.contains(NodeFlowSyntax.text(parts.get(0)))) {
      return true;
    }
    if (!ARGUMENTS.equals(parts.get(1).kind())) {
      return false;
    }
    final String callee = NodeFlowSyntax.text(parts.get(0));
    return NUMERIC_CALL.matcher(callee).matches() || NUMERIC_METHOD.matcher(callee).matches();
  }
}
