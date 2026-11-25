package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.python.DigestUse.Kind;
import java.util.List;
import java.util.regex.Pattern;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** The uses of a digest in an operation and in an assignment. */
final class DigestOperands {
  private static final String EQUALS = "'='";
  private static final String ANNASSIGN = "annassign";
  private static final int BINARY = 3;
  private static final Pattern EXTENSION =
      Pattern.compile("^[rRbBuU]*(['\"])\\.[A-Za-z0-9]+\\1$");

  private DigestOperands() {}

  static DigestStep arithmetic(final SyntaxNode parent, final SyntaxNode value, final int up) {
    final List<SyntaxNode> parts = parent.children();
    if (parts.size() == DigestTree.SINGLE) {
      return DigestStep.up(up);
    }
    final String operator = parts.size() == BINARY ? DigestTree.text(parts.get(1)) : "";
    if ("+".equals(operator) && isExtension(otherOperand(parts, value))) {
      return DigestStep.done(Kind.BENIGN, "the stem of a file name");
    }
    return "+".equals(operator) || "%".equals(operator)
        ? DigestStep.up(up)
        : DigestStep.done(Kind.UNKNOWN, "arithmetic");
  }

  private static SyntaxNode otherOperand(final List<SyntaxNode> parts, final SyntaxNode value) {
    return parts.get(0) == value ? parts.get(2) : parts.get(0);
  }

  private static boolean isExtension(final SyntaxNode operand) {
    final SyntaxNode leaf = DigestTree.collapse(operand);
    return "STRING".equals(leaf.kind()) && EXTENSION.matcher(DigestTree.text(leaf)).matches();
  }

  static DigestStep assignment(final SyntaxNode statement, final SyntaxNode value) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.size() == DigestTree.SINGLE) {
      return DigestStep.done(Kind.NEUTRAL, "dropped");
    }
    final String second = parts.get(1).kind();
    if (!EQUALS.equals(second) && !ANNASSIGN.equals(second)) {
      return DigestStep.done(Kind.UNKNOWN, "an augmented assignment");
    }
    final boolean inValue = parts.get(parts.size() - 1) == value || ANNASSIGN.equals(second);
    return inValue && onlyNames(parts)
        ? DigestStep.done(Kind.NEUTRAL, "assigned")
        : DigestStep.done(Kind.UNKNOWN, "stored in a field or an item");
  }

  private static boolean onlyNames(final List<SyntaxNode> parts) {
    boolean names = true;
    for (int index = 0; index + 1 < parts.size(); index++) {
      final SyntaxNode part = parts.get(index);
      if (!EQUALS.equals(part.kind()) && !ANNASSIGN.equals(part.kind())) {
        names &= "NAME".equals(DigestTree.collapse(part).kind());
      }
    }
    return names;
  }
}
