package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The package and imports of one Java file, as qualified names with dots. Static imports are kept
 * apart, because they name members, not types.
 *
 * @param packageName the declared package, empty for the default package
 * @param single the single-type imports: simple name to qualified name
 * @param onDemand the packages or types imported with {@code .*}
 * @param staticSingle the single static imports: member name to the qualified names of its owners
 * @param staticOnDemand the types imported with {@code import static T.*}
 */
record Imports(
    String packageName,
    Map<String, String> single,
    List<String> onDemand,
    Map<String, List<String>> staticSingle,
    List<String> staticOnDemand) {

  private static final String PACKAGE = "packageDeclaration";
  private static final String IMPORT = "importDeclaration";
  private static final String STATIC = "static";
  private static final String WILDCARD = "*";

  Imports {
    single = Map.copyOf(single);
    onDemand = List.copyOf(onDemand);
    staticSingle = Map.copyOf(staticSingle);
    staticOnDemand = List.copyOf(staticOnDemand);
  }

  /** One {@code import} line. */
  private record Declaration(String name, boolean wildcard, boolean isStatic) {
    String simpleName() {
      return name.substring(name.lastIndexOf('.') + 1);
    }

    String owner() {
      return name.substring(0, Math.max(name.lastIndexOf('.'), 0));
    }
  }

  /** Reads the package and imports of the compilation unit {@code root}. */
  static Imports of(final SyntaxNode root) {
    final String packageName =
        root.children().stream()
            .filter(child -> PACKAGE.equals(child.kind()))
            .findFirst()
            .flatMap(Imports::nameOf)
            .orElse("");
    final List<Declaration> declarations =
        root.children().stream()
            .filter(child -> IMPORT.equals(child.kind()))
            .map(Imports::declaration)
            .flatMap(Optional::stream)
            .toList();
    return new Imports(
        packageName,
        declarations.stream()
            .filter(found -> !found.wildcard() && !found.isStatic())
            .collect(Collectors.toMap(Declaration::simpleName, Declaration::name, (a, b) -> b)),
        declarations.stream()
            .filter(found -> found.wildcard() && !found.isStatic())
            .map(Declaration::name)
            .toList(),
        declarations.stream()
            .filter(found -> !found.wildcard() && found.isStatic())
            .collect(
                Collectors.groupingBy(
                    Declaration::simpleName,
                    Collectors.mapping(Declaration::owner, Collectors.toList()))),
        declarations.stream()
            .filter(found -> found.wildcard() && found.isStatic())
            .map(Declaration::name)
            .toList());
  }

  /** Returns the imports of a file that declares package {@code packageName} and imports none. */
  static Imports none(final String packageName) {
    return new Imports(packageName, Map.of(), List.of(), Map.of(), List.of());
  }

  private static Optional<Declaration> declaration(final SyntaxNode node) {
    return nameOf(node)
        .map(
            name ->
                new Declaration(
                    name, Syntax.hasToken(node, WILDCARD), Syntax.hasToken(node, STATIC)));
  }

  private static Optional<String> nameOf(final SyntaxNode declaration) {
    return Syntax.child(declaration, "qualifiedName").map(Syntax::text);
  }
}
