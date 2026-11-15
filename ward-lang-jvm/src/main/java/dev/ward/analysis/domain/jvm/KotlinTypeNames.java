package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads the package and imports of a Kotlin file, for the receiver types of its flow sites. */
final class KotlinTypeNames {

  private static final String PACKAGE_HEADER = "packageHeader";
  private static final String IMPORT_LIST = "importList";
  private static final String IMPORT_HEADER = "importHeader";
  private static final String IDENTIFIER = "identifier";
  private static final String IMPORT_ALIAS = "importAlias";
  private static final String WILDCARD = "*";
  private static final String DOT = ".";

  private KotlinTypeNames() {}

  /**
   * Reads the names of a file.
   *
   * @param root the root of the syntax tree of the file
   * @return the package and the imports, resolved the way the Java ones are
   */
  static JavaTypeNames of(final SyntaxNode root) {
    final Map<String, String> imports = new HashMap<>();
    final List<String> wildcards = new ArrayList<>();
    final StringBuilder packageName = new StringBuilder();
    visit(root, imports, wildcards, packageName);
    return JavaTypeNames.of(imports, wildcards, packageName.toString());
  }

  private static void visit(
      final SyntaxNode node,
      final Map<String, String> imports,
      final List<String> wildcards,
      final StringBuilder packageName) {
    switch (node.kind()) {
      case PACKAGE_HEADER -> packageName.append(nameOf(node));
      case IMPORT_HEADER -> addImport(node, imports, wildcards);
      case "kotlinFile", "preamble", IMPORT_LIST ->
          node.children().forEach(child -> visit(child, imports, wildcards, packageName));
      default -> {
        // Declarations come after the imports; nothing under them is read.
      }
    }
  }

  private static void addImport(
      final SyntaxNode node, final Map<String, String> imports, final List<String> wildcards) {
    final String name = nameOf(node);
    if (name.isEmpty()) {
      return;
    }
    if (KotlinFlowSyntax.hasToken(node, WILDCARD)) {
      wildcards.add(name);
      return;
    }
    final SyntaxNode alias = KotlinFlowSyntax.child(node, IMPORT_ALIAS);
    final String bound =
        alias == null ? name.substring(name.lastIndexOf(DOT) + 1) : aliasName(alias);
    imports.put(bound, name);
  }

  private static String aliasName(final SyntaxNode alias) {
    final String written = KotlinFlowSyntax.text(alias);
    return written.startsWith("as") ? written.substring("as".length()) : written;
  }

  private static String nameOf(final SyntaxNode node) {
    final SyntaxNode name = KotlinFlowSyntax.child(node, IDENTIFIER);
    return name == null ? "" : KotlinFlowSyntax.text(name);
  }
}
