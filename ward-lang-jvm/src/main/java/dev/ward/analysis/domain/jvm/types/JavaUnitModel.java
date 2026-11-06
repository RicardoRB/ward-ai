package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Reads the classes a Java file declares, with their members, from its syntax tree. */
final class JavaUnitModel {
  private final Imports imports;
  private final TypeScope scope;
  private final List<SourceModel.ClassDecl> classes = new ArrayList<>();
  private final Map<String, JvmClass> declared = new HashMap<>();

  private JavaUnitModel(final SyntaxNode tree, final Predicate<String> external) {
    this.imports = Imports.of(tree);
    final List<JavaClassReader.Entry> top = new ArrayList<>();
    for (final SyntaxNode child : tree.children()) {
      if ("typeDeclaration".equals(child.kind())) {
        JavaClassReader.topLevel(child).ifPresent(top::add);
      }
    }
    final Set<String> names = new HashSet<>();
    top.forEach(entry -> declaredNames(entry, qualified(imports.packageName()), names));
    this.scope = new TypeScope(imports, name -> names.contains(name) || external.test(name));
    final JavaClassReader reader = new JavaClassReader(scope);
    for (final JavaClassReader.Entry entry : top) {
      final SourceModel.ClassDecl declaration =
          reader.read(entry, qualified(imports.packageName()), scope);
      classes.add(declaration);
      flatten(declaration);
    }
  }

  /** Reads {@code tree}; {@code external} tells whether a class outside this file exists. */
  static JavaUnitModel of(final SyntaxNode tree, final Predicate<String> external) {
    return new JavaUnitModel(tree, external);
  }

  private static String qualified(final String packageName) {
    return packageName.isEmpty() ? "" : packageName + '.';
  }

  private void flatten(final SourceModel.ClassDecl declaration) {
    declared.put(declaration.jvm().name(), declaration.jvm());
    declaration.nested().forEach(this::flatten);
  }

  private static void declaredNames(
      final JavaClassReader.Entry entry, final String prefix, final Set<String> names) {
    final String binary = prefix + entry.name();
    names.add(binary);
    JavaClassReader.bodyEntries(entry.declaration()).stream()
        .filter(inner -> JavaClassReader.isType(inner.declaration()))
        .forEach(inner -> declaredNames(inner, binary + '$', names));
  }

  /** Returns the top-level classes. */
  List<SourceModel.ClassDecl> classes() {
    return classes;
  }

  /** Returns every class of the file by binary name, nested ones included. */
  Map<String, JvmClass> declared() {
    return declared;
  }

  TypeScope scope() {
    return scope;
  }
}
