package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Tells whether the writes of a field are plain statements of the bodies of constructors. */
final class ConstructorWrites {

  private static final int CONSTRUCTOR_PATH = 4;
  private static final int BLOCK_PATH = 3;
  private static final int BLOCK_STATEMENT_PATH = 2;

  private ConstructorWrites() {}

  /** Whether the write is a statement of the body of a constructor of the class itself. */
  static boolean topLevel(final List<SyntaxNode> up) {
    final int size = up.size();
    return size >= CONSTRUCTOR_PATH
        && "statement".equals(up.get(size - 1).kind())
        && "blockStatement".equals(up.get(size - BLOCK_STATEMENT_PATH).kind())
        && "block".equals(up.get(size - BLOCK_PATH).kind())
        && MemberDeclarations.CONSTRUCTOR.equals(up.get(size - CONSTRUCTOR_PATH).kind());
  }

  /** Whether each constructor writes once, or delegates to another one and writes nothing. */
  static boolean eachAssignsOnce(
      final List<SyntaxWalk.Visit> writes, final List<SyntaxNode> constructors) {
    return !constructors.isEmpty()
        && constructors.stream().allMatch(constructor -> assignsOnce(writes, constructor));
  }

  private static boolean assignsOnce(
      final List<SyntaxWalk.Visit> writes, final SyntaxNode constructor) {
    final long count =
        writes.stream()
            .filter(
                write ->
                    write
                        .ancestors()
                        .get(write.ancestors().size() - CONSTRUCTOR_PATH)
                        .equals(constructor))
            .count();
    return count == 1 || count == 0 && delegates(constructor);
  }

  private static boolean delegates(final SyntaxNode constructor) {
    return SyntaxWalk.text(constructor).replaceAll("^[^{]*\\{\\s*", "").startsWith("this(");
  }
}
