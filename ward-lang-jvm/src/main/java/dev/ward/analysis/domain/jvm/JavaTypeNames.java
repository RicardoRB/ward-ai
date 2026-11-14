package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
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
          "IllegalArgumentException",
          "IllegalStateException",
          "NullPointerException",
          "ProcessBuilder");
  private static final Set<String> PRIMITIVES =
      Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

  private final Map<String, String> imports;
  private final List<String> wildcards;
  private final String packageName;

  private JavaTypeNames(
      final Map<String, String> imports, final List<String> wildcards, final String packageName) {
    this.imports = imports;
    this.wildcards = wildcards;
    this.packageName = packageName;
  }

  /**
   * Reads the imports and package of a compilation unit. Static imports name no single type, so
   * they are not kept; the packages of wildcard imports are kept to resolve the types of {@link
   * JdkTypes}.
   *
   * @param root the root of the syntax tree of the unit
   * @return the names of the unit
   */
  static JavaTypeNames of(final SyntaxNode root) {
    final Map<String, String> imports = new HashMap<>();
    final List<String> wildcards = new ArrayList<>();
    String packageName = "";
    for (final SyntaxNode child : root.children()) {
      if (IMPORT_DECLARATION.equals(child.kind())) {
        addImport(child, imports, wildcards);
      } else if (PACKAGE_DECLARATION.equals(child.kind())) {
        packageName = nameOf(child);
      }
    }
    return new JavaTypeNames(imports, wildcards, packageName);
  }

  /**
   * Creates the names of a file from imports that another frontend read, as the Kotlin one does.
   *
   * @param imports the fully qualified name of each simple name an import binds
   * @param wildcards the packages of the wildcard imports
   * @param packageName the package of the file, or an empty string
   * @return the names of the file
   */
  static JavaTypeNames of(
      final Map<String, String> imports, final List<String> wildcards, final String packageName) {
    return new JavaTypeNames(Map.copyOf(imports), List.copyOf(wildcards), packageName);
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
    final String wildcard = JdkTypes.find(erased, wildcards);
    if (wildcard != null) {
      return Optional.of(wildcard);
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

  private static void addImport(
      final SyntaxNode node, final Map<String, String> imports, final List<String> wildcards) {
    final List<String> texts =
        JavaFlowSyntax.parts(node).stream().map(JavaFlowSyntax::text).toList();
    final String name = nameOf(node);
    if (name.isEmpty() || texts.contains(STATIC)) {
      return;
    }
    if (texts.contains(WILDCARD)) {
      wildcards.add(name);
    } else {
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
