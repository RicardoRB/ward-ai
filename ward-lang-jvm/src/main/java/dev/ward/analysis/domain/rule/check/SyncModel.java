package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the inconsistent-synchronization check reads from one class: its methods, the instance
 * fields it may report, and the private or protected methods that are only ever called with the
 * lock on {@code this} held. It is empty when the class has something the check cannot read, such
 * as two methods with the same name and parameter count, or a lock taken on a call result.
 */
final class SyncModel {

  private static final String FIELD = "fieldDeclaration";
  private static final String STATEMENT = "statement";
  private static final Set<String> NESTED =
      Set.of(
          "lambdaExpression",
          "classBody",
          "classDeclaration",
          "interfaceDeclaration",
          "enumDeclaration",
          "recordDeclaration");

  private final List<SyncMethod> methods;
  private final Map<String, SyntaxNode> fields;
  private final Set<String> instanceFields;
  private final Set<SyncMethod> locked;

  private SyncModel(
      final List<SyncMethod> methods,
      final Map<String, SyntaxNode> fields,
      final Set<String> instanceFields,
      final Set<SyncMethod> locked) {
    this.methods = List.copyOf(methods);
    this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    this.instanceFields = Set.copyOf(instanceFields);
    this.locked = Set.copyOf(locked);
  }

  List<SyncMethod> methods() {
    return methods;
  }

  /** The fields the check may report, by name, each with the node of its name. */
  Map<String, SyntaxNode> fields() {
    return fields;
  }

  /** Every non-static field of the class, of any visibility. */
  Set<String> instanceFields() {
    return instanceFields;
  }

  /** Tells whether the method is only called with the lock on {@code this} held. */
  boolean lockedMethod(final SyncMethod method) {
    return locked.contains(method);
  }

  /** Reads the class, or returns nothing when it holds something the check cannot read. */
  static Optional<SyncModel> of(final SyntaxNode clazz) {
    final List<JavaNodes.Member> members = JavaNodes.members(clazz);
    final List<SyncMethod> methods = SyncMethod.in(members);
    final Set<String> nested = nestedNames(clazz);
    final boolean readable =
        SyncMethod.distinct(methods)
            && methods.stream().noneMatch(m -> nested.contains(m.name()))
            && !locksOnCall(clazz);
    if (!readable) {
      return Optional.empty();
    }
    final Map<String, SyntaxNode> candidates = new LinkedHashMap<>();
    final Set<String> instance = new HashSet<>();
    fieldsOf(members, candidates, instance);
    candidates.keySet().removeAll(nested);
    candidates.keySet().removeIf(name -> inFinally(clazz, name));
    return Optional.of(
        new SyncModel(methods, candidates, instance, SyncLocking.lockedMethods(members, methods)));
  }

  private static void fieldsOf(
      final List<JavaNodes.Member> members,
      final Map<String, SyntaxNode> candidates,
      final Set<String> instance) {
    for (final JavaNodes.Member member : members) {
      if (FIELD.equals(member.node().kind()) && !member.has("static")) {
        final boolean candidate =
            !member.has("public")
                && !member.has("volatile")
                && !member.has("final")
                && !guardedByThis(member);
        for (final SyntaxNode id :
            SyntaxWalk.descendants(member.node()).stream()
                .filter(n -> "variableDeclaratorId".equals(n.kind()))
                .toList()) {
          instance.add(SyntaxWalk.text(id));
          if (candidate) {
            candidates.put(SyntaxWalk.text(id), id);
          }
        }
      }
    }
  }

  private static boolean guardedByThis(final JavaNodes.Member member) {
    return member.holder().children().stream()
        .map(SyntaxWalk::text)
        .anyMatch(text -> text.startsWith("@GuardedBy") && text.contains("\"this\""));
  }

  private static Set<String> nestedNames(final SyntaxNode clazz) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(clazz)) {
      if (NESTED.contains(node.kind())
          && !node.equals(JavaNodes.kid(clazz, "classBody").orElse(null))) {
        SyntaxWalk.leaves(node).stream()
            .filter(leaf -> "IDENTIFIER".equals(leaf.kind()))
            .forEach(leaf -> names.add(SyntaxWalk.text(leaf)));
      }
    }
    return names;
  }

  private static boolean locksOnCall(final SyntaxNode clazz) {
    return SyntaxWalk.visits(clazz, Set.of(STATEMENT)).stream()
        .map(v -> SyncLocking.lockOf(v.node()))
        .flatMap(Optional::stream)
        .anyMatch(lock -> lock.contains("("));
  }

  private static boolean inFinally(final SyntaxNode clazz, final String field) {
    return SyntaxWalk.visits(clazz, Set.of("finallyBlock")).stream()
        .anyMatch(
            v ->
                SyntaxWalk.leaves(v.node()).stream()
                    .anyMatch(l -> field.equals(SyntaxWalk.text(l))));
  }
}
