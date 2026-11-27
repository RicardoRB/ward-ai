package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the annotations written on the package of a {@code package-info.java}. */
final class PackageAnnotations {
  private static final String PACKAGE = "packageDeclaration";
  private static final String ANNOTATION = "annotation";

  private PackageAnnotations() {}

  /** Returns the qualified names, resolved through the single-type imports, in source order. */
  static List<String> of(final SyntaxNode tree) {
    final Imports imports = Imports.of(tree);
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode declaration : tree.children()) {
      if (!PACKAGE.equals(declaration.kind())) {
        continue;
      }
      for (final SyntaxNode child : declaration.children()) {
        if (ANNOTATION.equals(child.kind())) {
          Syntax.child(child, "qualifiedName")
              .map(name -> imports.single().getOrDefault(Syntax.text(name), Syntax.text(name)))
              .ifPresent(names::add);
        }
      }
    }
    return names;
  }
}
