package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Tells how an expression that names a field uses it: read, write or null check. */
final class SyncRoles {

  /** The field is read. */
  static final int READ = 0;

  /** The field is written. */
  static final int WRITE = 1;

  /** The field is compared with {@code null}. */
  static final int NULL = 2;

  private static final String EXPRESSION = "expression";
  private static final Set<String> NOT_ASSIGNMENTS = Set.of("'=='", "'!='", "'<='", "'>='");
  private static final int BINARY = 3;
  private static final int UNARY = 2;

  private SyncRoles() {}

  /** Returns the roles of an access: 0 read, 1 write, 2 null check. */
  static int[] of(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (up.isEmpty() || !EXPRESSION.equals(up.get(up.size() - 1).kind())) {
      return new int[] {READ};
    }
    final List<SyntaxNode> parts = up.get(up.size() - 1).children();
    final boolean first = parts.get(0).span().equals(visit.node().span());
    if (parts.size() == BINARY) {
      return binaryRoles(parts, first);
    }
    if (parts.size() == UNARY && increment(parts.get(first ? 1 : 0).kind())) {
      return new int[] {READ, WRITE};
    }
    return new int[] {READ};
  }

  private static int[] binaryRoles(final List<SyntaxNode> parts, final boolean first) {
    final String operator = parts.get(1).kind();
    if (first && assignment(operator)) {
      return "'='".equals(operator) ? new int[] {WRITE} : new int[] {READ, WRITE};
    }
    if (comparison(operator) && "null".equals(SyntaxWalk.text(parts.get(first ? 2 : 0)))) {
      return new int[] {NULL};
    }
    return new int[] {READ};
  }

  private static boolean assignment(final String kind) {
    return kind.length() > 2
        && kind.startsWith("'")
        && kind.endsWith("='")
        && !NOT_ASSIGNMENTS.contains(kind);
  }

  private static boolean increment(final String kind) {
    return "'++'".equals(kind) || "'--'".equals(kind);
  }

  private static boolean comparison(final String kind) {
    return "'=='".equals(kind) || "'!='".equals(kind);
  }
}
