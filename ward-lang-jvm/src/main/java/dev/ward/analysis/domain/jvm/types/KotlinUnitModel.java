package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The classes a Kotlin file declares: their primary-constructor properties, properties and member
 * functions with declared types. Members without a declared type are left out, so they are unknown.
 */
final class KotlinUnitModel {
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String CLASS = "classDeclaration";
  private static final Set<String> MEMBER_KINDS =
      Set.of("functionDeclaration", "propertyDeclaration", CLASS, "objectDeclaration");

  private final Map<String, JvmClass> declared = new HashMap<>();
  private final List<Declared> classes = new ArrayList<>();
  private final TypeScope scope;

  /** A class with the syntax it came from. */
  record Declared(JvmClass jvm, SyntaxNode node, TypeScope scope, List<SyntaxNode> members) {}

  private KotlinUnitModel(final SyntaxNode tree, final Predicate<String> external) {
    final Imports imports = KotlinTypes.imports(tree);
    final List<SyntaxNode> tops = new ArrayList<>();
    collectClasses(tree, tops);
    final Set<String> names = new HashSet<>();
    final String prefix = imports.packageName().isEmpty() ? "" : imports.packageName() + '.';
    tops.forEach(node -> names.add(prefix + nameOf(node)));
    this.scope = new TypeScope(imports, name -> names.contains(name) || external.test(name));
    for (final SyntaxNode node : tops) {
      final Declared read = KotlinClassReader.read(node, prefix + nameOf(node), scope);
      classes.add(read);
      declared.put(read.jvm().name(), read.jvm());
    }
  }

  static KotlinUnitModel of(final SyntaxNode tree, final Predicate<String> external) {
    return new KotlinUnitModel(tree, external);
  }

  Map<String, JvmClass> declared() {
    return declared;
  }

  List<Declared> classes() {
    return classes;
  }

  TypeScope scope() {
    return scope;
  }

  private static void collectClasses(final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (CLASS.equals(child.kind())) {
        out.add(child);
      } else if ("kotlinFile".equals(child.kind()) || "topLevelObject".equals(child.kind())) {
        collectClasses(child, out);
      }
    }
    if ("kotlinFile".equals(node.kind())) {
      return;
    }
  }

  private static String nameOf(final SyntaxNode declaration) {
    return Syntax.child(declaration, SIMPLE_IDENTIFIER).map(Syntax::text).orElse("");
  }

  /** Returns the member declarations of a class, wherever the grammar put the class body. */
  static List<SyntaxNode> members(final SyntaxNode declaration) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : declaration.children()) {
      collectMembers(child, found);
    }
    return found;
  }

  private static void collectMembers(final SyntaxNode node, final List<SyntaxNode> found) {
    if (MEMBER_KINDS.contains(node.kind())) {
      found.add(node);
      return;
    }
    node.children().forEach(child -> collectMembers(child, found));
  }
}
