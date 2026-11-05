package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The imports and package of one Java file, which give the fully qualified names of the types that
 * the flow sites report. A single-type import wins; otherwise the type is taken to live in the
 * file's package, and a few {@code java.lang} names are known by name.
 */
final class JavaTypeNames {

  private static final String IMPORT_DECLARATION = "importDeclaration";
  private static final String PACKAGE_DECLARATION = "packageDeclaration";
  private static final String QUALIFIED_NAME = "qualifiedName";
  private static final String STATIC = "static";
  private static final String WILDCARD = "*";
  private static final String DOT = ".";
  private static final Set<String> JAVA_LANG =
      Set.of(
          "Object",
          "String",
          "StringBuilder",
          "Runtime",
          "System",
          "Math",
          "Thread",
          "Class",
          "Integer",
          "Long",
          "Boolean",
          "Character",
          "Byte",
          "Short",
          "Double",
          "Float",
          "Exception",
          "RuntimeException",
          "Throwable",
          "Error",
          "ProcessBuilder");
  private static final Set<String> PRIMITIVES =
      Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

  private final Map<String, String> imports;
  private final String packageName;

  private JavaTypeNames(final Map<String, String> imports, final String packageName) {
    this.imports = imports;
    this.packageName = packageName;
  }

  /**
   * Reads the imports and package of a compilation unit. Static and wildcard imports name no single
   * type, so they are not kept.
   *
   * @param root the root of the syntax tree of the unit
   * @return the names of the unit
   */
  static JavaTypeNames of(final SyntaxNode root) {
    final Map<String, String> imports = new HashMap<>();
    String packageName = "";
    for (final SyntaxNode child : root.children()) {
      if (IMPORT_DECLARATION.equals(child.kind())) {
        addImport(child, imports);
      } else if (PACKAGE_DECLARATION.equals(child.kind())) {
        packageName = nameOf(child);
      }
    }
    return new JavaTypeNames(imports, packageName);
  }

  /**
   * Resolves a written type, such as {@code List<String>} or {@code Statement}, to its fully
   * qualified name.
   *
   * @param written the type as written in a declaration
   * @return the qualified name, or empty for a primitive type
   */
  Optional<String> resolve(final String written) {
    final String erased = erased(written);
    if (PRIMITIVES.contains(erased)) {
      return Optional.empty();
    }
    if (erased.contains(DOT)) {
      return Optional.of(erased);
    }
    if (imports.containsKey(erased)) {
      return Optional.of(imports.get(erased));
    }
    if (JAVA_LANG.contains(erased)) {
      return Optional.of("java.lang." + erased);
    }
    return Optional.of(packageName.isEmpty() ? erased : packageName + DOT + erased);
  }

  /**
   * Resolves a type name used as a qualifier, such as {@code Runtime} in {@code
   * Runtime.getRuntime()}. Only a simple name that starts with an upper-case letter can name a
   * type.
   *
   * @param qualifier the qualifier text as written
   * @return the qualified name, or empty when the qualifier is not a type name
   */
  Optional<String> resolveTypeName(final String qualifier) {
    if (qualifier.isEmpty()
        || qualifier.contains(DOT)
        || !Character.isUpperCase(qualifier.charAt(0))) {
      return Optional.empty();
    }
    return resolve(qualifier);
  }

  /**
   * Resolves an annotation name through the imports; a name that is not imported stays as written.
   *
   * @param written the annotation name as written
   * @return the imported name, or the written name
   */
  String annotation(final String written) {
    return imports.getOrDefault(written, written);
  }

  private static void addImport(final SyntaxNode node, final Map<String, String> imports) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(node);
    final boolean named =
        parts.stream()
            .map(JavaFlowSyntax::text)
            .noneMatch(text -> STATIC.equals(text) || WILDCARD.equals(text));
    final String name = nameOf(node);
    if (named && !name.isEmpty()) {
      imports.put(name.substring(name.lastIndexOf('.') + 1), name);
    }
  }

  private static String nameOf(final SyntaxNode node) {
    final SyntaxNode name = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(node), QUALIFIED_NAME);
    return name == null ? "" : JavaFlowSyntax.text(name).replaceAll("\\s", "");
  }

  private static String erased(final String written) {
    final String compact = written.replaceAll("\\s", "");
    final int generics = compact.indexOf('<');
    final String raw = generics < 0 ? compact : compact.substring(0, generics);
    return raw.replace("[]", "");
  }
}
