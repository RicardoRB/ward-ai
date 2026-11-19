package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The names a macro call reads. A macro's arguments are tokens that the compiler resolves after
 * expansion, so an identifier among them counts as a use, unless it follows {@code .}, {@code ::}
 * or {@code $}; the string literals of a formatting macro are read for the names in their braces.
 */
final class RustScopeMacros {
  private static final Set<String> FORMAT_MACROS =
      Set.of(
          "println",
          "print",
          "eprintln",
          "eprint",
          "format",
          "write",
          "writeln",
          "panic",
          "assert",
          "assert_eq",
          "assert_ne",
          "debug_assert",
          "debug_assert_eq",
          "debug_assert_ne",
          "unreachable",
          "todo",
          "unimplemented",
          "format_args"
      );
  private static final Set<String> IDENTIFIERS =
      Set.of("NON_KEYWORD_IDENTIFIER", "RAW_IDENTIFIER", RustKinds.SELF);
  private static final Set<String> NOT_A_NAME = Set.of(".", "::", "$");
  private static final String TEXT = "text";
  private static final String PATH = "simplePath";

  private final RustScopeWalk walk;

  RustScopeMacros(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /** A macro call: the identifiers among its tokens and the names in a format string are read. */
  void invocation(final SyntaxNode node) {
    final SyntaxNode path = RustTrees.child(node, PATH);
    final boolean formats = path != null && FORMAT_MACROS.contains(lastSegment(path));
    final List<SyntaxNode> leaves = new ArrayList<>();
    node.children().stream()
        .filter(child -> !PATH.equals(child.kind()))
        .forEach(child -> leaves(child, leaves));
    for (int index = 0; index < leaves.size(); index++) {
      final SyntaxNode leaf = leaves.get(index);
      if (isName(leaves, index)) {
        walk.builder.reference(RustTrees.name(leaf), leaf.span(), ReferenceAccess.READ);
      } else if (formats && RustKinds.STRING_LITERAL.equals(leaf.kind())) {
        RustFormatNames.read(walk.builder, leaf);
      }
    }
  }

  private static String lastSegment(final SyntaxNode path) {
    final List<SyntaxNode> segments = RustTrees.children(path, "simplePathSegment");
    return RustTrees.text(segments.get(segments.size() - 1));
  }

  private static void leaves(final SyntaxNode node, final List<SyntaxNode> out) {
    if (node.children().isEmpty()) {
      out.add(node);
    }
    node.children().forEach(child -> leaves(child, out));
  }

  private static boolean isName(final List<SyntaxNode> leaves, final int index) {
    if (!IDENTIFIERS.contains(leaves.get(index).kind())) {
      return false;
    }
    return index == 0
        || !NOT_A_NAME.contains(leaves.get(index - 1).fields().getOrDefault(TEXT, ""));
  }
}
