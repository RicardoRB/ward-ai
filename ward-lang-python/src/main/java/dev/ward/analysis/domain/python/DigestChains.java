package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.DigestUse.Kind;
import java.util.List;
import java.util.Set;

/** The uses of a digest in a chain of calls, attributes and subscripts, as in {@code h.hexdigest()}. */
final class DigestChains {
  private static final String DOT = "'.'";
  private static final String UPDATE = "update";
  private static final Set<String> VALUE_METHODS =
      Set.of("hexdigest", "digest", "copy", "encode", "decode", "hex", "lower", "upper", "strip");

  private DigestChains() {}

  /** A node of an atom_expr: the base, or a trailer that holds the value. */
  static DigestStep inChain(final List<SyntaxNode> path, final int parent, final int child) {
    final SyntaxNode trailer = path.get(parent).children().get(child);
    if (!"trailer".equals(trailer.kind())) {
      return walk(path, parent, child + 1);
    }
    if (DigestTree.OPEN_INDEX.equals(trailer.children().get(0).kind())) {
      return DigestStep.done(Kind.BENIGN, "a key");
    }
    return callee(path, parent, child);
  }

  private static DigestStep callee(final List<SyntaxNode> path, final int parent, final int child) {
    final String name = DigestTree.tokens(path.get(parent).children().subList(0, child));
    return switch (DigestCallees.of(name)) {
      case SECURITY -> DigestStep.done(Kind.SECURITY, "passed to " + name);
      case BENIGN -> DigestStep.done(Kind.BENIGN, "passed to " + name);
      case CARRIES -> walk(path, parent, child + 1);
      default -> DigestStep.done(Kind.UNKNOWN, "passed to " + name);
    };
  }

  /** Follows the trailers after a value: the methods and slices that only carry it on. */
  static DigestStep walk(final List<SyntaxNode> path, final int parent, final int from) {
    final List<SyntaxNode> parts = path.get(parent).children();
    int index = from;
    while (index < parts.size()) {
      final SyntaxNode trailer = parts.get(index);
      final String head = trailer.children().get(0).kind();
      if (DigestTree.OPEN_INDEX.equals(head)) {
        index++;
      } else if (DOT.equals(head) && index + 1 < parts.size()
          && DigestTree.isCallTrailer(parts.get(index + 1))) {
        final String method = DigestTree.text(DigestTree.collapse(trailer.children().get(1)));
        if (UPDATE.equals(method)) {
          return DigestStep.done(Kind.NEUTRAL, "fed data");
        }
        if (!VALUE_METHODS.contains(method)) {
          return DigestStep.done(Kind.UNKNOWN, "the method " + method);
        }
        index += 2;
      } else {
        return DigestStep.done(Kind.UNKNOWN, "an attribute or a call");
      }
    }
    return DigestStep.up(parent);
  }
}
