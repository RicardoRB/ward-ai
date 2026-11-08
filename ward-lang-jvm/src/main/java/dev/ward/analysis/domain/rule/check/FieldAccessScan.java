package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the accesses a class makes to its own fields, with the lock held at each one, and the calls
 * its methods make to each other. The thread-safety ports share it. Constructors, initializers,
 * lambda bodies and nested class bodies are not scanned: SpotBugs treats them as separate or
 * unlocked code, and their lock state is not known here.
 */
final class FieldAccessScan {

  private static final Set<String> SKIPPED = Set.of("readObject", "clone", "close", "finalize");

  private final Map<String, Field> fields = new LinkedHashMap<>();
  private final CallGraph graph = new CallGraph();
  private final AccessWalker walker;

  /** The lock that is held at a point of a method. */
  enum Lock {
    /** No lock. */
    NONE,
    /** The lock of {@code this}. */
    THIS,
    /** Some other lock, or the lock of the class. */
    OTHER
  }

  /** Whether an access reads or writes a field. */
  enum Kind {
    /** The field is read. */
    READ,
    /** The field is assigned, incremented or decremented. */
    WRITE
  }

  /**
   * A field of the class.
   *
   * @param name the field name
   * @param type the compact text of its type
   * @param modifiers its modifier keywords
   * @param node the field declaration
   */
  record Field(String name, String type, List<String> modifiers, SyntaxNode node) {
    Field {
      modifiers = List.copyOf(modifiers);
    }

    boolean has(final String modifier) {
      return modifiers.contains(modifier);
    }

    Field named(final String other) {
      return new Field(other, type, modifiers, node);
    }
  }

  /**
   * One access to a field.
   *
   * @param field the field
   * @param node the node of the access
   * @param kind read or write
   * @param arithmetic whether a write also reads the field in an arithmetic operation
   * @param receiver whether a method is called on the field
   * @param lock the lock held in the method at the access
   * @param method the name of the method that holds the access
   */
  record Access(
      Field field,
      SyntaxNode node,
      Kind kind,
      boolean arithmetic,
      boolean receiver,
      Lock lock,
      String method) {}

  /** Scans the class declaration {@code clazz}. */
  FieldAccessScan(final SyntaxNode clazz) {
    this(clazz, true);
  }

  private FieldAccessScan(final SyntaxNode clazz, final boolean methods) {
    ClassMembers.fieldMembers(clazz).forEach(this::field);
    walker = new AccessWalker(fields, graph);
    if (methods) {
      ClassShapes.methods(clazz).forEach(this::method);
      graph.resolve();
    }
  }

  /** Reads only the fields of {@code clazz}; regions are added with {@link #scanRegion}. */
  static FieldAccessScan fieldsOnly(final SyntaxNode clazz) {
    return new FieldAccessScan(clazz, false);
  }

  /** Adds the accesses and calls of a region of code that runs as the method {@code key}. */
  void scanRegion(
      final SyntaxNode region, final Lock base, final String key, final Set<String> shadow) {
    walker.walk(region, new AccessWalker.Site(base, key, shadow), List.of());
  }

  /** Returns the names of the methods of this class that the method {@code caller} calls. */
  Set<String> callees(final String caller) {
    return graph.callees(caller);
  }

  /** Returns the fields of the class by name. */
  Map<String, Field> fields() {
    return Map.copyOf(fields);
  }

  /** Returns the accesses in source order. */
  List<Access> accesses() {
    return walker.accesses();
  }

  /** Returns whether the access is made with the lock of {@code this} held. */
  boolean locked(final Access access) {
    return access.lock() == Lock.THIS || graph.locked(access.method());
  }

  /** Returns whether a method can run with no lock held. */
  boolean reachable(final String method) {
    return graph.reachable(method);
  }

  private void field(final SyntaxNode member) {
    final SyntaxNode declaration = ClassMembers.declarationOf(member);
    final String type = ClassMembers.typeText(declaration).orElse("");
    final Field base = new Field("", type, MemberDeclarations.modifiers(member), declaration);
    ClassMembers.fieldNames(declaration).forEach(name -> fields.put(name, base.named(name)));
  }

  private void method(final SyntaxNode member) {
    final SyntaxNode declaration = ClassMembers.declarationOf(member);
    final String name = MemberDeclarations.name(declaration);
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    graph.declare(name, modifiers);
    if (!SKIPPED.contains(name)) {
      scanRegion(declaration, baseLock(modifiers), name, ClassShapes.declaredNames(declaration));
    }
  }

  private static Lock baseLock(final List<String> modifiers) {
    if (!modifiers.contains("synchronized")) {
      return Lock.NONE;
    }
    return modifiers.contains("static") ? Lock.OTHER : Lock.THIS;
  }
}
