package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One pass over a Java compilation unit. Imports and the package are read from the top level;
 * declarations are qualified by their enclosing type, and type names come from the type positions.
 */
final class JavaFileScan {
  private static final String PACKAGE = "packageDeclaration";
  private static final String IMPORT = "importDeclaration";
  private static final Set<String> TYPE_DECLARATIONS =
      Set.of(
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration",
          "annotationTypeDeclaration");
  private static final Set<String> TYPE_POSITIONS =
      Set.of("typeType", "createdName", "expression", "qualifiedName");
  private static final String STATIC_KEYWORD = "static";
  private static final String ON_DEMAND = "*";
  private static final String IDENTIFIER_RULE = "identifier";

  private final String path;
  private final List<Symbol> declared = new ArrayList<>();
  private final List<Reference> references = new ArrayList<>();

  JavaFileScan(final String path) {
    this.path = path;
  }

  /** Summarizes the compilation unit under {@code root}. */
  FileSummary scan(final SyntaxNode root) {
    final String packageName = readHeader(root);
    visit(root, packageName);
    return new FileSummary(path, declared, references, declared);
  }

  /** Reads the package and the imports, which are direct children of the unit. */
  private String readHeader(final SyntaxNode root) {
    String packageName = "";
    for (final SyntaxNode child : root.children()) {
      if (PACKAGE.equals(child.kind())) {
        packageName = JavaSyntax.dotted(JavaSyntax.child(child, "qualifiedName"));
        references.add(new Reference("package:" + packageName, child.span()));
      } else if (IMPORT.equals(child.kind())) {
        references.add(importReference(child));
      }
    }
    return packageName;
  }

  private static Reference importReference(final SyntaxNode node) {
    final String name = JavaSyntax.dotted(JavaSyntax.child(node, "qualifiedName"));
    final String kind;
    if (JavaSyntax.hasTerminal(node, STATIC_KEYWORD)) {
      kind = "static";
    } else if (JavaSyntax.hasTerminal(node, ON_DEMAND)) {
      kind = "on-demand";
    } else {
      kind = "import";
    }
    return new Reference(kind + ":" + name, node.span());
  }

  private void visit(final SyntaxNode node, final String owner) {
    if (PACKAGE.equals(node.kind()) || IMPORT.equals(node.kind())) {
      return;
    }
    if (TYPE_DECLARATIONS.contains(node.kind())) {
      declare(node, owner);
      return;
    }
    if (TYPE_POSITIONS.contains(node.kind())) {
      final String name = JavaNameChain.of(node);
      if (!name.isEmpty()) {
        references.add(new Reference("name:" + name, node.span()));
      }
    }
    for (final SyntaxNode child : node.children()) {
      visit(child, owner);
    }
  }

  /** Declares a type under its owner and visits its members with it as the owner. */
  private void declare(final SyntaxNode node, final String owner) {
    final String name = JavaSyntax.dotted(JavaSyntax.child(node, IDENTIFIER_RULE));
    final String qualified = owner.isEmpty() ? name : owner + "." + name;
    declared.add(new Symbol(qualified, node.span()));
    for (final SyntaxNode child : node.children()) {
      visit(child, qualified);
    }
  }
}
