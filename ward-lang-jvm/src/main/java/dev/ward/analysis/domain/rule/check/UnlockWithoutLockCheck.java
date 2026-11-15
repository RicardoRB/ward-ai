package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a call of {@code unlock()} on a {@code java.util.concurrent.locks} lock in a method that
 * never acquires that lock: no {@code lock()}, {@code lockInterruptibly()} or {@code tryLock()} on
 * the same receiver. A method whose name contains {@code lock} is skipped, as SpotBugs does, and so
 * is a call whose receiver type is unknown. A method that calls a method of the file that acquires
 * the same lock, such as a private {@code tryLock()} helper, counts as acquiring it.
 */
public final class UnlockWithoutLockCheck implements RuleCheck {

  private static final String METHOD = "methodDeclaration";
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String UNLOCK = "unlock";
  private static final String LOCK_WORD = "lock";
  private static final Set<String> ACQUIRES = Set.of("lock", "lockInterruptibly", "tryLock");
  private static final int QUALIFIED_PARTS = 3;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final Set<String> lockTypes;
  private final String message;

  /**
   * Creates the check.
   *
   * @param lockTypes the qualified names of the lock types whose {@code unlock} counts
   * @param message the message reported at the call; {@code {value}} is the receiver
   */
  public UnlockWithoutLockCheck(final Set<String> lockTypes, final String message) {
    this.lockTypes = Set.copyOf(lockTypes);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    final List<SyntaxWalk.Visit> methods = SyntaxWalk.visits(root, Set.of(METHOD));
    final Map<String, Set<String>> acquiredByName = new HashMap<>();
    for (final SyntaxWalk.Visit visit : methods) {
      acquiredByName
          .computeIfAbsent(JavaNodes.name(visit.node()), name -> new HashSet<>())
          .addAll(acquires(visit.node()));
    }
    for (final SyntaxWalk.Visit visit : methods) {
      if (!JavaNodes.name(visit.node()).toLowerCase(Locale.ROOT).contains(LOCK_WORD)) {
        inMethod(visit.node(), acquiredByName, types, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void inMethod(
      final SyntaxNode method,
      final Map<String, Set<String>> acquiredByName,
      final UnitTypeFacts types,
      final List<RuleMatch> matches) {
    final List<SyntaxNode> calls =
        SyntaxWalk.descendants(method).stream()
            .filter(UnlockWithoutLockCheck::isQualified)
            .toList();
    final Set<String> acquired = new HashSet<>(acquires(method));
    localCallNames(method)
        .forEach(name -> acquired.addAll(acquiredByName.getOrDefault(name, Set.of())));
    for (final SyntaxNode call : calls) {
      final boolean unlock = UNLOCK.equals(MemberDeclarations.name(last(call)));
      if (unlock && !acquired.contains(receiver(call)) && isLock(call, types)) {
        matches.add(CheckSupport.at(call.span(), CheckSupport.fill(message, receiver(call), "")));
      }
    }
  }

  /** The receivers on which the method itself calls {@code lock}, {@code tryLock} and the like. */
  private static Set<String> acquires(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(UnlockWithoutLockCheck::isQualified)
        .filter(call -> ACQUIRES.contains(MemberDeclarations.name(last(call))))
        .map(UnlockWithoutLockCheck::receiver)
        .collect(Collectors.toSet());
  }

  /** The names of the methods the method calls on itself: {@code m()} and {@code this.m()}. */
  private static List<String> localCallNames(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> EXPRESSION.equals(node.kind()))
        .filter(UnlockWithoutLockCheck::isOwnCall)
        .map(call -> MemberDeclarations.name(last(call)))
        .toList();
  }

  private static boolean isOwnCall(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    return METHOD_CALL.equals(last(node).kind())
        && (parts.size() == 1 || isQualified(node) && "this".equals(receiver(node)));
  }

  private boolean isLock(final SyntaxNode call, final UnitTypeFacts types) {
    return types
        .callTarget(call.span())
        .filter(target -> lockTypes.contains(target.declaringType().name()))
        .isPresent();
  }

  private static boolean isQualified(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind())
        && node.children().size() == QUALIFIED_PARTS
        && METHOD_CALL.equals(last(node).kind());
  }

  private static SyntaxNode last(final SyntaxNode call) {
    return call.children().get(call.children().size() - 1);
  }

  private static String receiver(final SyntaxNode call) {
    return SyntaxWalk.text(call.children().get(0));
  }
}
