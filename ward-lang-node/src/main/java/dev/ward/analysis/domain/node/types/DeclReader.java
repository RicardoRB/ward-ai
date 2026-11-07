package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeImportBindings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the declaration index of a file from its parse tree: what it declares, imports, exports and
 * what its reference directives name. Statements the reader does not understand are skipped.
 */
public final class DeclReader {
  private static final Pattern REFERENCE =
      Pattern.compile("^///\\s*<reference\\s+(lib|path|types)\\s*=\\s*[\"']([^\"']+)[\"']");
  private static final String STATEMENT = "statement";
  private static final String EXPORT = "export";

  private final DeclSink sink;
  private final ExportReader exports;

  DeclReader(final DeclSink sink) {
    this.sink = sink;
    this.exports = new ExportReader(sink, this);
  }

  /**
   * Reads the file.
   *
   * @param tree the parse tree of the file
   * @param text the text of the file, for its reference directives
   * @return the declaration index
   */
  public static FileDecls read(final SyntaxNode tree, final String text) {
    final DeclSink sink = new DeclSink();
    new DeclReader(sink).elements(tree);
    final Map<String, FileDecls.Import> imports =
        NodeImportBindings.of(tree).stream()
            .collect(
                Collectors.toMap(
                    NodeImportBindings.Import::local,
                    binding -> new FileDecls.Import(binding.specifier(), binding.member()),
                    (first, second) -> second));
    final boolean isModule =
        !imports.isEmpty()
            || !sink.exports().isEmpty()
            || sink.decls().stream().anyMatch(decl -> decl.has(Decl.EXPORTED));
    return new FileDecls(
        isModule, imports, sink.decls(), sink.exports(), sink.modules(), references(text));
  }

  /** The key of the ambient declarations that augment the global scope. */
  public static String globalKey() {
    return NamespaceReader.globalKey();
  }

  private static List<String> references(final String text) {
    final List<String> found = new ArrayList<>();
    for (final String line : text.split("\n", -1)) {
      final String trimmed = line.trim();
      if (!isCommentOrBlank(trimmed)) {
        break;
      }
      final Matcher matcher = REFERENCE.matcher(trimmed);
      if (matcher.find()) {
        found.add(matcher.group(1) + ":" + matcher.group(2));
      }
    }
    return found;
  }

  private static boolean isCommentOrBlank(final String line) {
    return line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*");
  }

  /** Reads the elements under a program, source elements or statement list node. */
  void elements(final SyntaxNode node) {
    for (final SyntaxNode part : TreeKit.parts(node)) {
      switch (part.kind()) {
        case "sourceElements", "statementList" -> elements(part);
        case "sourceElement" -> sourceElement(part);
        case STATEMENT -> statement(part, 0);
        default -> {
          // tokens and the end of file declare nothing
        }
      }
    }
  }

  private void sourceElement(final SyntaxNode element) {
    final SyntaxNode statement = TreeKit.child(element, STATEMENT);
    if (statement != null) {
      statement(statement, TreeKit.hasToken(element, EXPORT) ? Decl.EXPORTED : 0);
    }
  }

  private void statement(final SyntaxNode statement, final int flags) {
    final boolean exported = TreeKit.hasToken(statement, EXPORT) || flags != 0;
    final int all = exported ? Decl.EXPORTED : 0;
    for (final SyntaxNode part : TreeKit.parts(statement)) {
      switch (part.kind()) {
        case STATEMENT -> statement(part, all);
        case "exportStatement" -> exports.exportStatement(part);
        case "expressionStatement" -> exports.expressionStatement(part, flags);
        default -> declaration(part, all);
      }
    }
  }

  /** Reads a declaration node, which is skipped when it is not one. */
  void declaration(final SyntaxNode node, final int flags) {
    switch (node.kind()) {
      case "variableStatement" -> sink.addAll(ValueDeclReader.variables(node, flags));
      case "functionDeclaration" -> sink.add(ValueDeclReader.function(node, flags));
      case "classDeclaration" -> sink.add(TypeDeclReader.clazz(node, flags));
      case "interfaceDeclaration" -> sink.add(TypeDeclReader.iface(node, flags));
      case "typeAliasDeclaration" -> sink.add(TypeDeclReader.alias(node, flags));
      case "enumDeclaration" -> sink.add(TypeDeclReader.enumeration(node, flags));
      case "namespaceDeclaration" -> NamespaceReader.namespace(node, flags, sink);
      default -> {
        // imports are read from the whole tree
      }
    }
  }
}
