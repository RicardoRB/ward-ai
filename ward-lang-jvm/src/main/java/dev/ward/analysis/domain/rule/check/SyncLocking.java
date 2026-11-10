package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds which methods of a class are only ever called with the lock on {@code this} held. */
final class SyncLocking {

  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final String SYNCHRONIZED = "'synchronized'";
  private static final String METHOD_CALL = "methodCall";
  private static final String THIS = "this";
  private static final int CALL_ON_THIS_PARTS = 3;

  private SyncLocking() {}

  /** Tells whether {@code ancestors} hold a {@code synchronized (this)} statement. */
  static boolean insideLockOnThis(final List<SyntaxNode> ancestors) {
    return ancestors.stream()
        .filter(a -> STATEMENT.equals(a.kind()))
        .anyMatch(a -> lockOf(a).filter(THIS::equals).isPresent());
  }

  /** Returns the text of the lock of a {@code synchronized} statement, if it is one. */
  static Optional<String> lockOf(final SyntaxNode statement) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.isEmpty() || !SYNCHRONIZED.equals(parts.get(0).kind())) {
      return Optional.empty();
    }
    return JavaNodes.kids(statement, EXPRESSION).stream().findFirst().map(SyntaxWalk::text);
  }

  /** A call of a method of the class on {@code this}, with where it sits. */
  private record Site(Optional<SyncMethod> caller, SyncMethod target, boolean lockHeld) {}

  static Set<SyncMethod> lockedMethods(
      final List<JavaNodes.Member> members, final List<SyncMethod> methods) {
    final List<Site> sites = new ArrayList<>();
    for (final SyncMethod method : methods) {
      sites.addAll(sitesIn(method.node(), Optional.of(method), method.locksThis(), methods));
    }
    members.stream()
        .filter(m -> "constructorDeclaration".equals(m.node().kind()))
        .forEach(m -> sites.addAll(sitesIn(m.node(), Optional.empty(), false, methods)));
    final Set<SyncMethod> held = heldMethods(sites, methods);
    held.retainAll(notUnlocked(sites, methods));
    return held;
  }

  /** The methods that no call site outside a held lock or a constructor-like method reaches. */
  private static Set<SyncMethod> notUnlocked(
      final List<Site> sites, final List<SyncMethod> methods) {
    final Set<SyncMethod> notUnlocked = new HashSet<>();
    methods.stream()
        .filter(m -> !m.isPublic() || SyncMethod.special(m.name()))
        .forEach(notUnlocked::add);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (final Site site : sites) {
        final boolean callerFree = site.caller().map(notUnlocked::contains).orElse(true);
        if (!site.lockHeld() && !callerFree && notUnlocked.remove(site.target())) {
          changed = true;
        }
      }
    }
    return notUnlocked;
  }

  /** The synchronized methods and every method that a held lock reaches. */
  private static Set<SyncMethod> heldMethods(
      final List<Site> sites, final List<SyncMethod> methods) {
    final Set<SyncMethod> held = new HashSet<>();
    methods.stream().filter(SyncMethod::locksThis).forEach(held::add);
    boolean changed = true;
    while (changed) {
      changed = false;
      for (final Site site : sites) {
        final boolean callerHeld = site.caller().map(held::contains).orElse(false);
        if ((site.lockHeld() || callerHeld) && held.add(site.target())) {
          changed = true;
        }
      }
    }
    return held;
  }

  private static List<Site> sitesIn(
      final SyntaxNode body,
      final Optional<SyncMethod> caller,
      final boolean wholeBodyLocked,
      final List<SyncMethod> methods) {
    final List<Site> sites = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(body, Set.of(EXPRESSION))) {
      callOn(visit.node())
          .ifPresent(
              call ->
                  methods.stream()
                      .filter(
                          m ->
                              m.name().equals(JavaNodes.name(call))
                                  && m.arity() == argumentCount(call))
                      .findFirst()
                      .ifPresent(
                          target ->
                              sites.add(
                                  new Site(
                                      caller,
                                      target,
                                      wholeBodyLocked || insideLockOnThis(visit.ancestors())))));
    }
    return sites;
  }

  /** Returns the method call of {@code m(...)} or {@code this.m(...)}. */
  private static Optional<SyntaxNode> callOn(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    if (parts.size() == 1 && METHOD_CALL.equals(parts.get(0).kind())) {
      return Optional.of(parts.get(0));
    }
    final boolean onThis =
        parts.size() == CALL_ON_THIS_PARTS
            && EXPRESSION.equals(parts.get(0).kind())
            && THIS.equals(SyntaxWalk.text(parts.get(0)))
            && METHOD_CALL.equals(parts.get(2).kind());
    return onThis ? Optional.of(parts.get(2)) : Optional.empty();
  }

  private static int argumentCount(final SyntaxNode call) {
    return SyntaxWalk.descendants(call).stream()
        .filter(node -> "expressionList".equals(node.kind()))
        .findFirst()
        .map(list -> JavaNodes.kids(list, EXPRESSION).size())
        .orElse(0);
  }
}
