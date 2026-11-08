package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports code that relies on the identity of a value-based class: synchronizing on one, calling
 * {@code wait}, {@code notify} or {@code notifyAll} on one, {@code System.identityHashCode} of one,
 * a weak, soft or phantom reference to one, a weak or identity map keyed by them, and a Guava or
 * Caffeine cache built with weak keys or weak or soft values of one. The classes are the primitive
 * wrappers and the JDK's documented value-based classes. A type the facts do not give is never
 * reported.
 */
public final class ValueClassIdentityCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final int MEMBER_PARTS = 3;
  private static final String BOOLEAN = "java.lang.Boolean";
  private static final Set<String> REFERENCES =
      Set.of(
          "java.lang.ref.WeakReference",
          "java.lang.ref.SoftReference",
          "java.lang.ref.PhantomReference");
  private static final Set<String> WAITING = Set.of("wait", "notify", "notifyAll");
  private static final Set<String> BUILDERS =
      Set.of("com.google.common.cache.CacheBuilder", "com.github.benmanes.caffeine.cache.Caffeine");
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION, STATEMENT), Set.of(FactKind.TYPE, FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the expression or statement
   */
  public ValueClassIdentityCheck(final String message) {
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
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION, STATEMENT))) {
      if (relies(visit.node(), types)) {
        found.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean relies(final SyntaxNode node, final UnitTypeFacts types) {
    if (STATEMENT.equals(node.kind())) {
      return synchronizedOn(node, types);
    }
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 2 && "new".equals(SyntaxWalk.text(parts.get(0)))) {
      return types.typeOf(node.span()).filter(ValueClassIdentityCheck::identityKeyed).isPresent();
    }
    return types.callTarget(node.span()).filter(target -> call(node, target, types)).isPresent();
  }

  private static boolean synchronizedOn(final SyntaxNode statement, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = statement.children();
    return !parts.isEmpty()
        && "synchronized".equals(SyntaxWalk.text(parts.get(0)))
        && SyntaxWalk.descendants(statement).stream()
            .filter(inner -> EXPRESSION.equals(inner.kind()))
            .findFirst()
            .flatMap(lock -> types.typeOf(lock.span()))
            .filter(ValueClasses::is)
            .isPresent();
  }

  /** Tells whether a created type is a reference or identity-keyed map of a value class. */
  private static boolean identityKeyed(final TypeRef type) {
    final List<TypeRef> args = type.arguments();
    if (args.isEmpty()) {
      return false;
    }
    final boolean first = ValueClasses.is(args.get(0));
    if (REFERENCES.contains(type.name()) || "java.util.WeakHashMap".equals(type.name())) {
      return first;
    }
    return "java.util.IdentityHashMap".equals(type.name())
        && (first
            || args.size() > 1
                && ValueClasses.is(args.get(1))
                && !BOOLEAN.equals(args.get(1).name()));
  }

  private static boolean call(
      final SyntaxNode node, final CallTarget target, final UnitTypeFacts types) {
    final String owner = target.declaringType().name();
    if ("java.lang.System".equals(owner) && "identityHashCode".equals(target.name())) {
      return CallArgumentNodes.first(node)
          .flatMap(arg -> types.typeOf(arg.span()))
          .filter(ValueClasses::is)
          .isPresent();
    }
    if ("java.lang.Object".equals(owner) && WAITING.contains(target.name())) {
      return receiver(node)
          .flatMap(recv -> types.typeOf(recv.span()))
          .filter(ValueClasses::is)
          .isPresent();
    }
    return cache(node, target, types);
  }

  private static Optional<SyntaxNode> receiver(final SyntaxNode call) {
    final List<SyntaxNode> parts = call.children();
    return parts.size() == MEMBER_PARTS && ".".equals(SyntaxWalk.text(parts.get(1)))
        ? Optional.of(parts.get(0))
        : Optional.empty();
  }

  /** A cache or map built with weak keys or weak or soft values of a value class. */
  private static boolean cache(
      final SyntaxNode node, final CallTarget target, final UnitTypeFacts types) {
    final boolean builds =
        BUILDERS.contains(target.declaringType().name())
                && Set.of("build", "buildAsync").contains(target.name())
            || "com.google.common.collect.MapMaker".equals(target.declaringType().name())
                && "makeMap".equals(target.name());
    final Optional<List<TypeRef>> args =
        types.typeOf(node.span()).map(TypeRef::arguments).filter(a -> a.size() >= 2);
    if (!builds || args.isEmpty()) {
      return false;
    }
    final List<String> chain = chain(node);
    return chain.contains("weakKeys") && ValueClasses.is(args.get().get(0))
        || (chain.contains("weakValues") || chain.contains("softValues"))
            && ValueClasses.is(args.get().get(1));
  }

  /** The names of the methods called along the receivers of a call. */
  private static List<String> chain(final SyntaxNode call) {
    final List<String> names = new ArrayList<>();
    Optional<SyntaxNode> current = Optional.of(call);
    while (current.isPresent()) {
      final List<SyntaxNode> parts = current.get().children();
      if (parts.size() == MEMBER_PARTS && ".".equals(SyntaxWalk.text(parts.get(1)))) {
        names.add(NullOperands.methodName(parts.get(2)));
      }
      current = receiver(current.get());
    }
    return names;
  }
}
