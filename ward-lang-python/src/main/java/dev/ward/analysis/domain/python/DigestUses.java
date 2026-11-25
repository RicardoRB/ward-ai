package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.DigestUse.Kind;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads, from the syntax around a value, what a use of a digest does with it. The value is a call
 * that makes a digest, or a read of a variable that holds one. The use is found by climbing from the
 * value through what only carries it on (a conversion, a slice, a string that is built with it, an
 * assignment to a plain name) to the first thing that does something with it: a call, a comparison,
 * a return, a store. Anything that this class does not recognise is {@link Kind#UNKNOWN}, so a use
 * is never taken for harmless by default.
 *
 * <p>shortcut: callees are told by their written text ({@code os.path.join}, {@code
 * hmac.compare_digest}), not by the import that binds them; upgrade by resolving the callee through
 * the scopes.
 */
public final class DigestUses {
  private static final String NO_CALL = "a call that is not in the tree";

  private DigestUses() {}

  /**
   * Classifies the use of the value that a call returns.
   *
   * @param root the syntax tree of the file
   * @param site the span of the callee of the call, such as {@code hashlib.md5}
   * @return the use, unknown when the call is not found
   */
  public static DigestUse ofCall(final SyntaxNode root, final Span site) {
    final List<SyntaxNode> path = new ArrayList<>();
    final int call = DigestTree.pathToCall(root, site, path) ? callIndex(path, site) : -1;
    if (call < 0) {
      return DigestUse.of(Kind.UNKNOWN, NO_CALL);
    }
    final DigestStep step = DigestChains.walk(path, path.size() - 1, call + 1);
    return step.use().orElseGet(() -> DigestClimb.climb(path, step.next()));
  }

  /**
   * The text of the arguments of the call whose callee is at {@code site}, such as {@code (x,
   * usedforsecurity=False)}.
   *
   * @param root the syntax tree of the file
   * @param site the span of the callee of the call
   * @return the text of the argument list without spaces, empty when the call is not found
   */
  public static String callArgumentsText(final SyntaxNode root, final Span site) {
    final List<SyntaxNode> path = new ArrayList<>();
    final int call = DigestTree.pathToCall(root, site, path) ? callIndex(path, site) : -1;
    return call < 0
        ? ""
        : DigestTree.tokens(List.of(path.get(path.size() - 1).children().get(call)));
  }

  /** The index of the call trailer that follows the callee, in the atom_expr that ends the path. */
  private static int callIndex(final List<SyntaxNode> path, final Span site) {
    final List<SyntaxNode> parts = path.get(path.size() - 1).children();
    for (int index = 1; index < parts.size(); index++) {
      if (DigestTree.isCallTrailer(parts.get(index))
          && parts.get(index).span().startOffset() >= site.endOffset()) {
        return index;
      }
    }
    return -1;
  }

  /**
   * Classifies the use of the variable read at a span.
   *
   * @param root the syntax tree of the file
   * @param read where the variable is read
   * @return the use, unknown when the read is not found
   */
  public static DigestUse ofRead(final SyntaxNode root, final Span read) {
    final List<SyntaxNode> path = new ArrayList<>();
    return DigestTree.pathToRead(root, read, path)
        ? DigestClimb.climb(path, path.size() - 1)
        : DigestUse.of(Kind.UNKNOWN, "a read that is not in the tree");
  }
}
