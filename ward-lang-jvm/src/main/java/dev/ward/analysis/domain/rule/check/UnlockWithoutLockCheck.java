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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of {@code unlock()} on a {@code java.util.concurrent.locks} lock in a method that
 * never acquires that lock: no {@code lock()}, {@code lockInterruptibly()} or {@code tryLock()} on
 * the same receiver. A method whose name contains {@code lock} is skipped, as SpotBugs does, and so
 * is a call whose receiver type is unknown.
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD))) {
      if (!JavaNodes.name(visit.node()).toLowerCase(Locale.ROOT).contains(LOCK_WORD)) {
        inMethod(visit.node(), types, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void inMethod(
      final SyntaxNode method, final UnitTypeFacts types, final List<RuleMatch> matches) {
    final List<SyntaxNode> calls =
        SyntaxWalk.descendants(method).stream()
            .filter(UnlockWithoutLockCheck::isQualified)
            .toList();
    final Set<String> acquired = new java.util.HashSet<>();
    calls.stream()
        .filter(call -> ACQUIRES.contains(MemberDeclarations.name(last(call))))
        .forEach(call -> acquired.add(receiver(call)));
    for (final SyntaxNode call : calls) {
      final boolean unlock = UNLOCK.equals(MemberDeclarations.name(last(call)));
      if (unlock && !acquired.contains(receiver(call)) && isLock(call, types)) {
        matches.add(CheckSupport.at(call.span(), CheckSupport.fill(message, receiver(call), "")));
      }
    }
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
