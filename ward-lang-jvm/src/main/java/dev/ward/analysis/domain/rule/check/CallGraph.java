package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The calls a class's methods make to each other, with the lock held at each call. It tells which
 * non-public methods only run with the lock of {@code this} held and which methods can run with no
 * lock held.
 */
final class CallGraph {

  private final List<Call> calls = new ArrayList<>();
  private final Set<String> publicMethods = new HashSet<>();
  private final Set<String> nonPrivateMethods = new HashSet<>();
  private final Set<String> declaredMethods = new HashSet<>();
  private final Set<String> lockedMethods = new HashSet<>();

  private record Call(String callee, String caller, FieldAccessScan.Lock lock) {}

  /** Records a method of the class with its modifier keywords. */
  void declare(final String name, final List<String> modifiers) {
    declaredMethods.add(name);
    if (modifiers.contains("public")) {
      publicMethods.add(name);
    }
    if (!modifiers.contains("private")) {
      nonPrivateMethods.add(name);
    }
  }

  /** Records a call of {@code callee} made by {@code caller} with {@code lock} held. */
  void call(final String callee, final String caller, final FieldAccessScan.Lock lock) {
    calls.add(new Call(callee, caller, lock));
  }

  /** Works out which non-public methods are only called with the lock of this held. */
  void resolve() {
    boolean changed = true;
    while (changed) {
      changed = false;
      for (final String name : declaredMethods) {
        if (!publicMethods.contains(name) && !lockedMethods.contains(name) && onlyLocked(name)) {
          lockedMethods.add(name);
          changed = true;
        }
      }
    }
  }

  /** Returns whether a method is only called with the lock of this held. */
  boolean locked(final String method) {
    return lockedMethods.contains(method);
  }

  /** Returns whether a method is not private or is called, with no lock, by one that is not. */
  boolean reachable(final String method) {
    return reachable(method, new HashSet<>());
  }

  /** Returns the names of the methods that {@code caller} calls. */
  Set<String> callees(final String caller) {
    final Set<String> names = new HashSet<>();
    calls.stream()
        .filter(call -> call.caller().equals(caller))
        .forEach(call -> names.add(call.callee()));
    return names;
  }

  private boolean reachable(final String method, final Set<String> visited) {
    return nonPrivateMethods.contains(method)
        || visited.add(method)
            && calls.stream()
                .filter(call -> call.callee().equals(method) && !call.caller().equals(method))
                .filter(call -> call.lock() == FieldAccessScan.Lock.NONE)
                .anyMatch(call -> reachable(call.caller(), visited));
  }

  private boolean onlyLocked(final String name) {
    final List<Call> sites =
        calls.stream()
            .filter(call -> call.callee().equals(name) && !call.caller().equals(name))
            .toList();
    return !sites.isEmpty()
        && sites.stream()
            .allMatch(
                call ->
                    call.lock() == FieldAccessScan.Lock.THIS
                        || lockedMethods.contains(call.caller()));
  }
}
