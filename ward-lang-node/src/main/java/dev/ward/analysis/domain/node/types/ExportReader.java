package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads export statements, including the export forms the grammar reads as expressions. */
final class ExportReader {
  private static final String DEFAULT = "default";
  private static final String STAR = "*";
  private static final String SINGLE = "singleExpression";
  private static final int ASSIGNMENT_PARTS = 3;

  private final DeclSink sink;
  private final DeclReader reader;

  ExportReader(final DeclSink sink, final DeclReader reader) {
    this.sink = sink;
    this.reader = reader;
  }

  /** Reads an {@code exportStatement}. */
  void exportStatement(final SyntaxNode node) {
    final boolean isDefault = TreeKit.hasToken(node, DEFAULT);
    final int flags = Decl.EXPORTED | (isDefault ? Decl.DEFAULT : 0);
    final SyntaxNode block = TreeKit.child(node, "exportFromBlock");
    final SyntaxNode declaration = TreeKit.child(node, "declaration");
    if (block != null) {
      exportBlock(block);
    } else if (declaration != null) {
      TreeKit.parts(declaration).forEach(part -> reader.declaration(part, flags));
    } else if (isDefault) {
      defaultExpression(TreeKit.child(node, SINGLE), flags);
    }
  }

  private void exportBlock(final SyntaxNode block) {
    final SyntaxNode from = TreeKit.child(block, "importFrom");
    final String specifier = from == null ? "" : TreeText.unquote(stringOf(from));
    final SyntaxNode namespace = TreeKit.child(block, "importNamespace");
    if (namespace != null) {
      final List<String> leaves = TreeText.leaves(namespace);
      final String exported = leaves.size() > 1 ? leaves.get(leaves.size() - 1) : STAR;
      sink.add(new FileDecls.Export(exported, STAR, specifier));
      return;
    }
    final SyntaxNode items = TreeKit.child(block, "exportModuleItems");
    if (items != null) {
      TreeKit.children(items, "exportAliasName").forEach(item -> exportItem(item, specifier));
    }
  }

  private void exportItem(final SyntaxNode item, final String specifier) {
    final List<SyntaxNode> names = TreeKit.children(item, "moduleExportName");
    final String local = TreeText.unquote(TreeText.text(names.get(0)));
    final String exported = TreeText.unquote(TreeText.text(names.get(names.size() - 1)));
    sink.add(new FileDecls.Export(exported, local, specifier));
  }

  private static String stringOf(final SyntaxNode from) {
    return TreeText.leaves(from).stream()
        .filter(leaf -> leaf.length() > 1 && "\"'".indexOf(leaf.charAt(0)) >= 0)
        .findFirst()
        .orElse("");
  }

  /** Reads {@code export default <expression>} and the {@code export = x} forms of the grammar. */
  void expressionStatement(final SyntaxNode node, final int flags) {
    final SyntaxNode sequence = TreeKit.child(node, "expressionSequence");
    final SyntaxNode expression = sequence == null ? null : TreeKit.child(sequence, SINGLE);
    if (expression == null) {
      return;
    }
    final List<SyntaxNode> parts = TreeKit.parts(expression);
    if (parts.size() == 2 && DEFAULT.equals(TreeText.text(parts.get(0)))) {
      defaultExpression(parts.get(1), flags | Decl.EXPORTED | Decl.DEFAULT);
    } else if (parts.size() == ASSIGNMENT_PARTS
        && "export".equals(TreeText.text(parts.get(0)))
        && "=".equals(TreeText.text(parts.get(1)))) {
      sink.add(new FileDecls.Export("=", TreeText.text(parts.get(2)), ""));
    }
  }

  private void defaultExpression(final SyntaxNode expression, final int flags) {
    if (expression == null) {
      return;
    }
    final SyntaxNode function = TreeKit.find(expression, "functionDeclaration");
    if (function != null && TreeKit.child(expression, "anonymousFunction") != null) {
      sink.add(ValueDeclReader.function(function, flags));
    } else if (TreeKit.hasToken(expression, "class")) {
      sink.add(TypeDeclReader.clazz(expression, flags));
    } else {
      sink.add(new FileDecls.Export(DEFAULT, TreeText.text(expression), ""));
    }
  }
}
