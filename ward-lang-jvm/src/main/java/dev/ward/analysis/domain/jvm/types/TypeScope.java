package dev.ward.analysis.domain.jvm.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a type name is written: the file's package and imports, the classes around it and the type
 * variables in scope. It turns a written name into a binary name by the order of JLS 6.4 and 6.5:
 * type variables, member classes of the enclosing classes, single-type imports, the package,
 * on-demand imports and {@code java.lang}.
 */
final class TypeScope {
  private static final String JAVA_LANG = "java.lang";

  private final Imports imports;
  private final List<String> enclosing;
  private final Set<String> typeVariables;
  private final Predicate<String> exists;

  /**
   * Creates the scope of a file.
   *
   * @param imports the package and imports of the file
   * @param exists whether a class with a given binary name exists, in the project or a library
   */
  TypeScope(final Imports imports, final Predicate<String> exists) {
    this(imports, List.of(), Set.of(), exists);
  }

  private TypeScope(
      final Imports imports,
      final List<String> enclosing,
      final Set<String> typeVariables,
      final Predicate<String> exists) {
    this.imports = imports;
    this.enclosing = List.copyOf(enclosing);
    this.typeVariables = Set.copyOf(typeVariables);
    this.exists = exists;
  }

  Imports imports() {
    return imports;
  }

  /** Returns the binary names of the enclosing classes, innermost first. */
  List<String> enclosing() {
    return enclosing;
  }

  /** Returns the scope inside the class {@code binaryName}, whose type parameters are {@code names}. */
  TypeScope inClass(final String binaryName, final Collection<String> names) {
    final List<String> classes = new ArrayList<>();
    classes.add(binaryName);
    classes.addAll(enclosing);
    final Set<String> variables = new HashSet<>(typeVariables);
    variables.addAll(names);
    return new TypeScope(imports, classes, variables, exists);
  }

  /** Returns the scope with {@code names} added as type variables, such as a method's. */
  TypeScope withTypeVariables(final Collection<String> names) {
    final Set<String> variables = new HashSet<>(typeVariables);
    variables.addAll(names);
    return new TypeScope(imports, enclosing, variables, exists);
  }

  boolean isTypeVariable(final String name) {
    return typeVariables.contains(name);
  }

  boolean exists(final String binaryName) {
    return exists.test(binaryName);
  }

  /** Resolves a type name written as segments, such as {@code [Map, Entry]}. */
  Optional<String> resolve(final List<String> segments) {
    if (segments.isEmpty()) {
      return Optional.empty();
    }
    final Optional<String> first = resolveSimple(segments.get(0));
    if (first.isPresent()) {
      return members(first.get(), segments.subList(1, segments.size()));
    }
    return binaryOf(String.join(".", segments));
  }

  private Optional<String> members(final String owner, final List<String> names) {
    String current = owner;
    for (final String name : names) {
      current = current + '$' + name;
      if (!exists.test(current)) {
        return Optional.empty();
      }
    }
    return Optional.of(current);
  }

  private Optional<String> resolveSimple(final String name) {
    for (final String outer : enclosing) {
      if (simpleName(outer).equals(name)) {
        return Optional.of(outer);
      }
      if (exists.test(outer + '$' + name)) {
        return Optional.of(outer + '$' + name);
      }
    }
    final String imported = imports.single().get(name);
    if (imported != null) {
      final Optional<String> found = binaryOf(imported);
      if (found.isPresent()) {
        return found;
      }
    }
    final String samePackage = qualified(imports.packageName(), name);
    if (exists.test(samePackage)) {
      return Optional.of(samePackage);
    }
    for (final String container : imports.onDemand()) {
      final Optional<String> found = binaryOf(container + '.' + name);
      if (found.isPresent()) {
        return found;
      }
    }
    final String implicit = JAVA_LANG + '.' + name;
    return exists.test(implicit) ? Optional.of(implicit) : Optional.empty();
  }

  /**
   * Turns a qualified name with dots into a binary name by trying each split into a package and
   * nested classes, longest package first.
   */
  Optional<String> binaryOf(final String dotted) {
    final String[] parts = dotted.split("\\.");
    for (int classStart = parts.length - 1; classStart >= 0; classStart--) {
      final String packageName = String.join(".", List.of(parts).subList(0, classStart));
      final String classes = String.join("$", List.of(parts).subList(classStart, parts.length));
      final String candidate = qualified(packageName, classes);
      if (exists.test(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  /** Returns the binary names of the classes that may declare the static member {@code name}. */
  List<String> staticOwners(final String name) {
    final List<String> owners = new ArrayList<>();
    for (final String owner : imports.staticSingle().getOrDefault(name, List.of())) {
      binaryOf(owner).ifPresent(owners::add);
    }
    for (final String owner : imports.staticOnDemand()) {
      binaryOf(owner).ifPresent(owners::add);
    }
    return owners;
  }

  /** Returns the simple name of a binary name: the part after the last dot or {@code $}. */
  static String simpleName(final String binaryName) {
    final int cut = Math.max(binaryName.lastIndexOf('.'), binaryName.lastIndexOf('$'));
    return binaryName.substring(cut + 1);
  }

  private static String qualified(final String packageName, final String name) {
    return packageName.isEmpty() ? name : packageName + '.' + name;
  }
}
