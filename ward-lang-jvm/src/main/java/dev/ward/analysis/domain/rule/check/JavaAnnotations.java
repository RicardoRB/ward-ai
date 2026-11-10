package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the annotations of a declaration holder and the imports of a file. */
final class JavaAnnotations {

  private static final Set<String> HOLDERS =
      Set.of("modifier", "classOrInterfaceModifier", "variableModifier");

  private JavaAnnotations() {}

  /** Returns the annotation nodes among the modifiers of {@code holder}. */
  static List<SyntaxNode> of(final SyntaxNode holder) {
    return holder.children().stream()
        .filter(child -> HOLDERS.contains(child.kind()))
        .flatMap(child -> SyntaxWalk.descendants(child).stream())
        .filter(node -> "annotation".equals(node.kind()))
        .toList();
  }

  /** Returns the modifier keywords among the modifiers of {@code holder}, without annotations. */
  static List<String> keywords(final SyntaxNode holder) {
    return holder.children().stream()
        .filter(child -> HOLDERS.contains(child.kind()))
        .filter(
            child ->
                SyntaxWalk.descendants(child).stream()
                    .noneMatch(n -> "annotation".equals(n.kind())))
        .map(SyntaxWalk::text)
        .toList();
  }

  /**
   * Returns the simple name of an annotation, such as {@code Test} for {@code @org.junit.Test(a)}.
   */
  static String simpleName(final SyntaxNode annotation) {
    final String text = SyntaxWalk.text(annotation);
    final int open = text.indexOf('(');
    final String name = open < 0 ? text : text.substring(0, open);
    return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('@')) + 1);
  }

  /** Returns the imports of the file: single types by qualified name, packages as {@code p.*}. */
  static Set<String> imports(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode declaration : JavaNodes.kids(root, "importDeclaration")) {
      final boolean statik = JavaNodes.kid(declaration, "'static'").isPresent();
      final boolean star = JavaNodes.kid(declaration, "'*'").isPresent();
      JavaNodes.kid(declaration, "qualifiedName")
          .filter(name -> !statik)
          .ifPresent(name -> names.add(SyntaxWalk.text(name) + (star ? ".*" : "")));
    }
    return names;
  }

  /**
   * Tells whether {@code simple} names {@code qualified} here, by an import of it or of its
   * package.
   */
  static boolean resolves(final Set<String> imports, final String simple, final String qualified) {
    final String pack = qualified.substring(0, qualified.length() - simple.length() - 1);
    return imports.contains(qualified) || imports.contains(pack + ".*");
  }
}
