package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads variable and function declarations. */
final class ValueDeclReader {
  private static final String IDENTIFIER = "identifier";

  private ValueDeclReader() {}

  /** Returns the variables declared by a {@code variableStatement}. */
  static List<Decl> variables(final SyntaxNode node, final int flags) {
    final SyntaxNode modifier = TreeKit.child(node, "varModifier");
    final boolean isConst = modifier != null && "const".equals(TreeText.text(modifier));
    final SyntaxNode list = TreeKit.child(node, "variableDeclarationList");
    final List<Decl> found = new ArrayList<>();
    if (list == null) {
      return found;
    }
    for (final SyntaxNode declaration : TreeKit.children(list, "variableDeclaration")) {
      final SyntaxNode name = TreeText.variableName(declaration);
      if (name != null) {
        found.add(variable(declaration, TreeText.text(name), flags | (isConst ? Decl.CONST : 0)));
      }
    }
    return found;
  }

  private static Decl variable(final SyntaxNode declaration, final String name, final int flags) {
    final SyntaxNode annotation = TreeKit.child(declaration, "typeAnnotation");
    TsType type = SigReader.annotated(annotation);
    if (annotation == null && TreeKit.hasToken(declaration, "=")) {
      final List<SyntaxNode> expressions = TreeKit.children(declaration, "singleExpression");
      type = SimpleInit.of(expressions.get(expressions.size() - 1), (flags & Decl.CONST) != 0);
    }
    return new Decl(Decl.Kind.VARIABLE, name, Decl.Header.NONE, List.of(), type, List.of(), flags);
  }

  /** Returns the function that a {@code functionDeclaration} declares. */
  static Decl function(final SyntaxNode node, final int flags) {
    final SyntaxNode signature = TreeKit.child(node, "callSignature");
    final Sig sig = SigReader.of(signature == null ? node : signature);
    final Decl.Header header =
        new Decl.Header(sig.typeParams(), List.of(), List.of(), List.of(sig), List.of(), List.of());
    return new Decl(
        Decl.Kind.FUNCTION,
        TreeText.text(identifier(node)),
        header,
        List.of(),
        TsType.UNKNOWN,
        List.of(),
        flags);
  }

  /** Returns the identifier child of a declaration, or the declaration itself. */
  static SyntaxNode identifier(final SyntaxNode node) {
    final SyntaxNode identifier = TreeKit.child(node, IDENTIFIER);
    return identifier == null ? node : identifier;
  }
}
