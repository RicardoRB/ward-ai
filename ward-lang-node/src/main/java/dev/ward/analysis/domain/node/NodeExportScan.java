package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Reads the exported names of one file, and the top-level declarations, from its tokens. */
final class NodeExportScan {
  static final String STAR_EXPORT = "*:";
  private static final String EXPORT_MODULE_ITEMS = "exportModuleItems";
  private static final String EXPORT_ALIAS_NAME = "exportAliasName";

  private static final String IMPORT = "import";
  private static final String AS = "as";
  private static final String DEFAULT = "default";
  private static final String DOT = ".";
  private static final String MODULE = "module";
  private static final String EXPORTS = "exports";
  private static final String STRING = "StringLiteral";
  private static final String IDENTIFIER = "Identifier";
  private static final Set<String> SKIPPED_MODIFIERS = Set.of("declare", "async", "abstract");
  private static final Set<String> DECLARATIONS =
      Set.of("const", "let", "var", "function", "class", "interface", "type", "enum", "namespace");

  private final NodeTokens tokens;
  private final List<Symbol> exports = new ArrayList<>();
  private final List<Symbol> declared = new ArrayList<>();

  /** Creates the scan over {@code tokens}. */
  NodeExportScan(final NodeTokens tokens) {
    this.tokens = tokens;
  }

  /** The exported names, in source order. */
  List<Symbol> exports() {
    return exports.stream()
        .sorted(Comparator.comparingInt(symbol -> symbol.span().startOffset()))
        .toList();
  }

  List<Symbol> declared() {
    return List.copyOf(declared);
  }

  /** Records the names of the export statement that starts at {@code exportIndex}. */
  void statement(final int exportIndex) {
    addExportDeclaration(exportIndex);
  }

  /** Records the name a star re-export adds: its namespace, or the {@code *:} source otherwise. */
  void star(final int star, final String source) {
    if (tokens.isKeyword(star + 1, AS) && IDENTIFIER.equals(tokens.kind(star + 2))) {
      exports.add(new Symbol(tokens.text(star + 2), tokens.leaf(star + 2).span()));
    } else {
      exports.add(new Symbol(STAR_EXPORT + source, tokens.leaf(star).span()));
    }
  }

  /** Records the token at {@code i}: CommonJS named exports and top-level declarations. */
  void token(final int i, final int depth) {
    scanCommonJsExport(i);
    scanDeclaration(i, depth);
  }

  /** Records {@code exports.name} and {@code module.exports.name} as exported names. */
  private void scanCommonJsExport(final int i) {
    final boolean qualified = DOT.equals(tokens.text(i - 1)) && MODULE.equals(tokens.text(i - 2));
    final boolean bare = EXPORTS.equals(tokens.text(i)) && !DOT.equals(tokens.text(i - 1));
    if ((bare || qualified && EXPORTS.equals(tokens.text(i)))
        && DOT.equals(tokens.text(i + 1))
        && IDENTIFIER.equals(tokens.kind(i + 2))) {
      exports.add(new Symbol(tokens.text(i + 2), tokens.leaf(i + 2).span()));
    }
  }

  /** Adds the exported name of each item of an export list, read from the syntax tree. */
  void exportLists(final SyntaxNode node) {
    if (EXPORT_MODULE_ITEMS.equals(node.kind())) {
      node.children().stream()
          .filter(item -> EXPORT_ALIAS_NAME.equals(item.kind()))
          .forEach(item -> addExportedName(NodeLeaves.of(item)));
    }
    node.children().forEach(this::exportLists);
  }

  /** Adds the last name of an item: its alias when it has one, its name otherwise. */
  private void addExportedName(final List<SyntaxNode> leaves) {
    final SyntaxNode last = leaves.get(leaves.size() - 1);
    if (IDENTIFIER.equals(last.kind()) || STRING.equals(last.kind())) {
      exports.add(new Symbol(NodeTokens.unquote(NodeLeaves.text(last)), last.span()));
    }
  }

  /** Adds {@code export default} and the name of an exported declaration. */
  private void addExportDeclaration(final int exportIndex) {
    if (tokens.isKeyword(exportIndex + 1, DEFAULT)) {
      exports.add(new Symbol(DEFAULT, tokens.leaf(exportIndex + 1).span()));
      return;
    }
    int j = exportIndex + 1;
    while (SKIPPED_MODIFIERS.contains(tokens.text(j))) {
      j++;
    }
    if (DECLARATIONS.contains(tokens.text(j)) && IDENTIFIER.equals(tokens.kind(j + 1))) {
      exports.add(new Symbol(tokens.text(j + 1), tokens.leaf(j + 1).span()));
    }
  }

  /** Records a top-level declaration, exported or not. */
  private void scanDeclaration(final int i, final int depth) {
    if (depth == 0
        && DECLARATIONS.contains(tokens.text(i))
        && !tokens.isKeyword(i - 1, IMPORT)
        && IDENTIFIER.equals(tokens.kind(i + 1))) {
      declared.add(new Symbol(tokens.text(i + 1), tokens.leaf(i + 1).span()));
    }
  }
}
