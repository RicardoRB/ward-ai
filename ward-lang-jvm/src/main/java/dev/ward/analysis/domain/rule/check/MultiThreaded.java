package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Tells whether a class is multi-threaded the way SpotBugs' SharedVariableAtomicityDetector does.
 */
final class MultiThreaded {

  private static final Set<String> ATOMIC_EXTRA =
      Set.of("LongAdder", "DoubleAdder", "LongAccumulator", "DoubleAccumulator");
  private static final Set<String> LOCKS = Set.of("Lock", "ReentrantLock");
  private static final String ATOMIC_PACKAGE = "java.util.concurrent.atomic";
  private static final String LOCKS_PACKAGE = "java.util.concurrent.locks";

  private MultiThreaded() {}

  /** Returns whether the modifier texts of a class include {@code @NotThreadSafe}. */
  static boolean notThreadSafe(final List<String> modifiers) {
    return modifiers.stream()
        .anyMatch(text -> text.matches("^@(?:[\\w.]*\\.)?NotThreadSafe(?:\\(.*)?$"));
  }

  /** Returns whether the class declaration is a Runnable, holds a concurrency field or locks. */
  static boolean is(final SyntaxNode clazz, final Set<String> imports) {
    return ClassTypeNames.implemented(clazz).contains("Runnable")
        || ClassTypeNames.superclass(clazz).filter("Thread"::equals).isPresent()
        || fieldQualifies(clazz, imports)
        || ClassMembers.callableMembers(clazz).stream().anyMatch(MultiThreaded::locks);
  }

  private static boolean fieldQualifies(final SyntaxNode clazz, final Set<String> imports) {
    return ClassMembers.fieldMembers(clazz).stream()
        .anyMatch(
            field ->
                MemberDeclarations.modifiers(field).contains("volatile")
                    || concurrent(
                        ClassMembers.typeText(ClassMembers.declarationOf(field)).orElse(""),
                        imports));
  }

  private static boolean concurrent(final String type, final Set<String> imports) {
    final String simple = ClassMembers.simpleName(type.replaceAll("<.*>", ""));
    final boolean atomic = simple.startsWith("Atomic") || ATOMIC_EXTRA.contains(simple);
    return atomic && inPackage(type, simple, ATOMIC_PACKAGE, imports)
        || LOCKS.contains(simple) && inPackage(type, simple, LOCKS_PACKAGE, imports);
  }

  private static boolean inPackage(
      final String type, final String simple, final String pack, final Set<String> imports) {
    return type.startsWith(pack + ".")
        || JavaAnnotations.resolves(imports, simple, pack + "." + simple);
  }

  private static boolean locks(final SyntaxNode method) {
    final List<String> modifiers = MemberDeclarations.modifiers(method);
    return modifiers.contains("synchronized")
        || SyntaxWalk.descendants(method).stream()
            .anyMatch(
                node ->
                    "statement".equals(node.kind())
                        && !node.children().isEmpty()
                        && "'synchronized'".equals(node.children().get(0).kind()));
  }
}
