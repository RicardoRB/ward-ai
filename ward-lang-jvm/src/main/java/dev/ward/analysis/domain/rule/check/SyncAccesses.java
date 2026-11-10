package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Counts how the methods of a class read, write and null-check each field while the lock on {@code
 * this} is held or not, as SpotBugs' FindInconsistentSync2 does: a constructor and a few lifecycle
 * methods are not read, and the accesses of methods named like a constructor are counted as locked
 * and left out.
 */
final class SyncAccesses {

  /** Index of the locked reads in a count array. */
  static final int READ_LOCKED = 0;

  /** Index of the locked writes. */
  static final int WRITE_LOCKED = 1;

  /** Index of the locked null checks. */
  static final int NULL_LOCKED = 2;

  /** Index of the unlocked reads. */
  static final int READ_UNLOCKED = 3;

  /** Index of the unlocked writes. */
  static final int WRITE_UNLOCKED = 4;

  /** Index of the unlocked null checks. */
  static final int NULL_UNLOCKED = 5;

  /** Index of the accesses with the lock on {@code this} taken in the method itself. */
  static final int LOCAL_LOCKS = 6;

  /** Index of the unlocked accesses in methods that look like getters. */
  static final int GETTER = 7;

  private static final int COUNTS = 8;
  private static final int UNLOCKED = 3;
  private static final String EXPRESSION = "expression";
  private static final String THIS_DOT = "this.";
  private static final Set<String> SKIPPED = Set.of("readObject", "clone", "close", "finalize");
  private static final int INDEX = 4;

  private SyncAccesses() {}

  /** Returns the counts of each field the model may report. */
  static Map<String, int[]> scan(final SyncModel model) {
    final Map<String, int[]> counts = new HashMap<>();
    model.fields().keySet().forEach(name -> counts.put(name, new int[COUNTS]));
    for (final SyncMethod method : model.methods()) {
      if (!method.isStatic() && !SKIPPED.contains(method.name())) {
        scanMethod(model, method, counts);
      }
    }
    return counts;
  }

  private static void scanMethod(
      final SyncModel model, final SyncMethod method, final Map<String, int[]> counts) {
    final Set<String> locals = new HashSet<>();
    SyntaxWalk.descendants(method.node()).stream()
        .filter(n -> "variableDeclaratorId".equals(n.kind()))
        .forEach(n -> locals.add(SyntaxWalk.text(n)));
    final List<SyntaxWalk.Visit> visits = SyntaxWalk.visits(method.node(), Set.of(EXPRESSION));
    final boolean getter = possibleGetter(model, visits, locals);
    for (final SyntaxWalk.Visit visit : visits) {
      final Optional<String> field = reference(visit.node(), model.fields().keySet(), locals);
      if (field.isPresent()) {
        final Held held = held(model, method, visit, getter);
        final int[] count = counts.get(field.get());
        for (final int role : SyncRoles.of(visit)) {
          record(count, role, held, method.name());
        }
      }
    }
  }

  /** Where an access happens: whether the lock is taken here, held at all, and in a getter. */
  private record Held(boolean explicit, boolean locked, boolean getter) {}

  private static Held held(
      final SyncModel model,
      final SyncMethod method,
      final SyntaxWalk.Visit visit,
      final boolean getter) {
    final boolean explicit = method.locksThis() || SyncLocking.insideLockOnThis(visit.ancestors());
    return new Held(explicit, explicit || model.lockedMethod(method), getter);
  }

  private static void record(
      final int[] count, final int role, final Held held, final String methodName) {
    if (held.explicit()) {
      count[LOCAL_LOCKS]++;
    }
    if (!SyncMethod.special(methodName)) {
      count[(held.locked() ? 0 : UNLOCKED) + role]++;
      if (!held.locked() && held.getter()) {
        count[GETTER]++;
      }
    }
  }

  /** Returns the name of the field that the expression is, as {@code f} or {@code this.f}. */
  private static Optional<String> reference(
      final SyntaxNode expression, final Set<String> fields, final Set<String> locals) {
    final String text = SyntaxWalk.text(expression);
    final boolean qualified = text.startsWith(THIS_DOT);
    final String name = qualified ? text.substring(THIS_DOT.length()) : text;
    return fields.contains(name) && (qualified || !locals.contains(name))
        ? Optional.of(name)
        : Optional.empty();
  }

  /**
   * Tells whether the method may be a getter, which SpotBugs lowers the report for. It is not one
   * when it certainly reads two fields of the class, writes one, or indexes an array.
   */
  private static boolean possibleGetter(
      final SyncModel model, final List<SyntaxWalk.Visit> visits, final Set<String> locals) {
    int reads = 0;
    for (final SyntaxWalk.Visit visit : visits) {
      final List<SyntaxNode> parts = visit.node().children();
      if (parts.size() == INDEX && "'['".equals(parts.get(1).kind())) {
        return false;
      }
      if (reference(visit.node(), model.instanceFields(), locals).isPresent()) {
        reads++;
        if (java.util.Arrays.stream(SyncRoles.of(visit))
            .anyMatch(role -> role == SyncRoles.WRITE)) {
          return false;
        }
      }
    }
    return reads < 2;
  }
}
