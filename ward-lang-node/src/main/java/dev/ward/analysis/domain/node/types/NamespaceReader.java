package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads namespaces, ambient modules and global augmentations. */
final class NamespaceReader {
  private static final String GLOBAL = "*global*";

  private NamespaceReader() {}

  /** The key of the ambient declarations that augment the global scope. */
  static String globalKey() {
    return GLOBAL;
  }

  /** Reads a namespace declaration into {@code sink}. */
  static void namespace(final SyntaxNode node, final int flags, final DeclSink sink) {
    final DeclSink inner = new DeclSink();
    final SyntaxNode statements = TreeKit.child(node, "statementList");
    if (statements != null) {
      new DeclReader(inner).elements(statements);
    }
    final SyntaxNode name = TreeKit.child(node, "namespaceName");
    final SyntaxNode literal = literalName(node);
    if (literal != null) {
      sink.addModule(TreeText.unquote(TreeText.text(literal)), inner.decls());
    } else if (name == null) {
      sink.addModule(GLOBAL, inner.decls());
    } else {
      sink.add(
          nested(
              TreeKit.children(name, "identifier").stream().map(TreeText::text).toList(),
              inner.decls(),
              flags));
    }
  }

  private static SyntaxNode literalName(final SyntaxNode node) {
    return TreeKit.parts(node).stream()
        .filter(part -> part.children().isEmpty() && "StringLiteral".equals(part.kind()))
        .findFirst()
        .orElse(null);
  }

  private static Decl nested(final List<String> names, final List<Decl> body, final int flags) {
    Decl current = namespace(names.get(names.size() - 1), body, flags);
    for (int index = names.size() - 2; index >= 0; index--) {
      current = namespace(names.get(index), List.of(current), flags);
    }
    return current;
  }

  private static Decl namespace(final String name, final List<Decl> body, final int flags) {
    return new Decl(
        Decl.Kind.NAMESPACE, name, Decl.Header.NONE, List.of(), TsType.UNKNOWN, body, flags);
  }
}
