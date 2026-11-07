package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the names the import declarations of one file bind, from the syntax tree. The grammar
 * produces the type-only markers, the specifier lists and the module string as nodes, so the names
 * are read from them directly.
 */
final class NodeImportScan {

  private static final String IMPORT_STATEMENT = "importStatement";
  private static final String IMPORT_EQUALS = "importEqualsDeclaration";
  private static final String IMPORT_FROM_BLOCK = "importFromBlock";
  private static final String IMPORT_FROM = "importFrom";
  private static final String IMPORT_DEFAULT = "importDefault";
  private static final String IMPORT_NAMESPACE = "importNamespace";
  private static final String IMPORT_MODULE_ITEMS = "importModuleItems";
  private static final String IMPORT_ALIAS_NAME = "importAliasName";
  private static final String MODULE_EXPORT_NAME = "moduleExportName";
  private static final String IMPORTED_BINDING = "importedBinding";
  private static final String TYPE_ONLY = "typeOnlyModifier";
  private static final String STRING = "StringLiteral";
  private static final String REQUIRE = "Require";
  private static final String DEFAULT = "default";
  private static final String STAR = "*";

  private final List<Binding> bindings = new ArrayList<>();
  private final List<Span> sites = new ArrayList<>();

  NodeImportScan(final SyntaxNode tree) {
    collect(tree);
  }

  /** The names the imports bind, in source order. */
  List<Binding> bindings() {
    return List.copyOf(bindings);
  }

  /** The regions of the import declarations, in source order. */
  List<Span> sites() {
    return List.copyOf(sites);
  }

  private void collect(final SyntaxNode node) {
    if (IMPORT_STATEMENT.equals(node.kind())) {
      importStatement(node);
    } else if (IMPORT_EQUALS.equals(node.kind())) {
      importEquals(node);
    } else {
      node.children().forEach(this::collect);
    }
  }

  private void importStatement(final SyntaxNode statement) {
    final SyntaxNode block = NodeLeaves.child(statement, IMPORT_FROM_BLOCK);
    final boolean typeOnly = NodeLeaves.hasChild(block, TYPE_ONLY);
    final SyntaxNode literal = moduleLiteral(block);
    if (literal == null) {
      sites.add(statement.span());
      return;
    }
    final String specifier = NodeTokens.unquote(NodeLeaves.text(literal));
    final Span site = NodeLeaves.site(statement, literal);
    for (final SyntaxNode part : block.children()) {
      if (IMPORT_DEFAULT.equals(part.kind())) {
        addDefault(part, typeOnly, specifier, site);
      } else if (IMPORT_NAMESPACE.equals(part.kind())) {
        addNamespace(part, typeOnly, specifier, site);
      } else if (IMPORT_MODULE_ITEMS.equals(part.kind())) {
        part.children().stream()
            .filter(item -> IMPORT_ALIAS_NAME.equals(item.kind()))
            .forEach(item -> addNamed(item, typeOnly, specifier, site));
      }
    }
    sites.add(statement.span());
  }

  /** Reads {@code import x = require('m')}, the only equals form that binds a module. */
  private void importEquals(final SyntaxNode declaration) {
    final List<SyntaxNode> leaves = NodeLeaves.of(declaration);
    final boolean requireForm = leaves.stream().anyMatch(leaf -> REQUIRE.equals(leaf.kind()));
    if (!requireForm) {
      return;
    }
    final SyntaxNode literal =
        leaves.stream().filter(leaf -> STRING.equals(leaf.kind())).findFirst().orElseThrow();
    final SyntaxNode name = leaves.get(1);
    final Span site = NodeLeaves.site(declaration, literal);
    bindings.add(
        new Binding(name, STAR, NodeTokens.unquote(NodeLeaves.text(literal)), false, site));
    sites.add(declaration.span());
  }

  /** A default import, or the default of a namespace-shaped part without a star. */
  private void addDefault(
      final SyntaxNode part, final boolean typeOnly, final String specifier, final Span site) {
    bindings.add(new Binding(NodeLeaves.of(part).get(0), DEFAULT, specifier, typeOnly, site));
  }

  /** {@code * as ns} binds the namespace; a bare name is the default export. */
  private void addNamespace(
      final SyntaxNode part, final boolean typeOnly, final String specifier, final Span site) {
    final List<SyntaxNode> leaves = NodeLeaves.of(part);
    if (STAR.equals(NodeLeaves.text(leaves.get(0)))) {
      bindings.add(new Binding(leaves.get(leaves.size() - 1), STAR, specifier, false, site));
    } else {
      bindings.add(new Binding(leaves.get(0), DEFAULT, specifier, typeOnly, site));
    }
  }

  /** {@code name}, {@code name as local}, {@code type name} or {@code "string" as local}. */
  private void addNamed(
      final SyntaxNode item, final boolean typeOnly, final String specifier, final Span site) {
    final List<SyntaxNode> exported = NodeLeaves.of(NodeLeaves.child(item, MODULE_EXPORT_NAME));
    final SyntaxNode binding = NodeLeaves.child(item, IMPORTED_BINDING);
    final SyntaxNode local =
        binding != null ? NodeLeaves.of(binding).get(0) : exported.get(exported.size() - 1);
    final boolean itemTypeOnly = typeOnly || NodeLeaves.hasChild(item, TYPE_ONLY);
    bindings.add(new Binding(local, memberName(exported.get(0)), specifier, itemTypeOnly, site));
  }

  /** The imported name: an identifier as written, or a string name without its quotes. */
  private static String memberName(final SyntaxNode name) {
    final String text = NodeLeaves.text(name);
    return STRING.equals(name.kind()) ? NodeTokens.unquote(text) : text;
  }

  /** The module string: a direct child of the block, or of its {@code from} clause. */
  private static SyntaxNode moduleLiteral(final SyntaxNode block) {
    final SyntaxNode from = NodeLeaves.child(block, IMPORT_FROM);
    if (from != null) {
      return NodeLeaves.of(from).stream()
          .filter(leaf -> STRING.equals(leaf.kind()))
          .findFirst()
          .orElse(null);
    }
    return block.children().stream()
        .filter(leaf -> STRING.equals(leaf.kind()))
        .findFirst()
        .orElse(null);
  }

  /**
   * One name an import binds.
   *
   * @param local the token of the name as the file uses it
   * @param member the imported name, {@code default} for a default import, or {@code *}
   * @param specifier the module as written, without quotes
   * @param typeOnly whether the import exists only for the type system
   * @param site the region from {@code import} to the module string
   */
  record Binding(SyntaxNode local, String member, String specifier, boolean typeOnly, Span site) {}
}
