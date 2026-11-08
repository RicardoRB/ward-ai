package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a method of an anonymous class that nothing can call: it is not private, abstract or a
 * special method, it overrides no method of the supertype, and no call or method reference in the
 * file names it with that many arguments. The type of the class cannot be named, so a method that
 * the supertype does not declare is dead unless the same expression calls it. It reports nothing
 * when the members of the supertype are unknown.
 */
public final class UncallableAnonymousMethodCheck implements RuleCheck {
  private static final String CREATOR = "creator";
  private static final String CLASS_BODY = "classBody";
  private static final String METHOD = "methodDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String OBJECT = "java.lang.Object";
  private static final int SHORTEST = 2;
  private static final Set<String> SPECIAL = Set.of("readResolve", "writeReplace");
  private static final Set<String> HIDING = Set.of("private", "abstract");
  private static final List<String> OBJECT_METHODS =
      List.of("toString/0", "hashCode/0", "equals/1", "clone/0", "finalize/0");
  private static final FactNeed NEED =
      new FactNeed(Set.of(CREATOR, "createdName"), Set.of(FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method
   */
  public UncallableAnonymousMethodCheck(final String message) {
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
    final Set<String> called = AnonymousCalls.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CREATOR))) {
      final SyntaxNode creator = visit.node();
      final Optional<TypeMembers> above = supertype(creator, types);
      final Optional<SyntaxNode> body = body(creator);
      if (above.isPresent() && body.isPresent()) {
        final Set<String> defined = signatures(above.get());
        declared(body.get()).stream()
            .filter(method -> dead(method, defined, called))
            .forEach(method -> found.add(CheckSupport.at(method.method().span(), message)));
      }
    }
    return List.copyOf(found);
  }

  /** Returns the members of the type that the creator names, unless it is plain {@code Object}. */
  private static Optional<TypeMembers> supertype(
      final SyntaxNode creator, final UnitTypeFacts types) {
    return creator.children().stream()
        .filter(part -> "createdName".equals(part.kind()))
        .findFirst()
        .filter(name -> types.typeOf(name.span()).filter(t -> !OBJECT.equals(t.name())).isPresent())
        .flatMap(name -> types.membersOf(name.span()));
  }

  private static Optional<SyntaxNode> body(final SyntaxNode creator) {
    return creator.children().stream()
        .filter(part -> "classCreatorRest".equals(part.kind()))
        .flatMap(rest -> rest.children().stream())
        .filter(part -> CLASS_BODY.equals(part.kind()))
        .findFirst();
  }

  /** Returns {@code name/arity} for each non-static method the supertype declares or inherits. */
  private static Set<String> signatures(final TypeMembers members) {
    final Set<String> out = new HashSet<>(OBJECT_METHODS);
    final List<CallTarget> all = new ArrayList<>(members.methods());
    all.addAll(members.inheritedMethods());
    all.stream()
        .filter(method -> !method.isStatic())
        .forEach(method -> out.add(method.name() + "/" + method.parameters().size()));
    return out;
  }

  /** A method of the class body with the text of its modifiers, in lower case. */
  private record Member(SyntaxNode method, String modifiers) {}

  private static List<Member> declared(final SyntaxNode body) {
    final List<Member> out = new ArrayList<>();
    for (final SyntaxNode entry : body.children()) {
      final String modifiers =
          entry.children().stream()
              .filter(part -> "modifier".equals(part.kind()))
              .map(SyntaxWalk::text)
              .reduce("", String::concat)
              .toLowerCase(Locale.ROOT);
      entry.children().stream()
          .filter(part -> "memberDeclaration".equals(part.kind()))
          .flatMap(member -> member.children().stream())
          .filter(part -> METHOD.equals(part.kind()))
          .forEach(method -> out.add(new Member(method, modifiers)));
    }
    return out;
  }

  private static boolean dead(
      final Member member, final Set<String> defined, final Set<String> called) {
    final String name = nameOf(member.method());
    final int arity = arity(member.method());
    final String modifiers = member.modifiers();
    final boolean special =
        name.length() < SHORTEST
            || name.indexOf('$') >= 0
            || SPECIAL.contains(name) && arity == 0
            || modifiers.contains("inject")
            || HIDING.stream().anyMatch(modifiers::contains);
    final String key = name + "/" + arity;
    final boolean isStatic = modifiers.contains("static");
    return !special
        && !called.contains(key)
        && !called.contains(name + "/*")
        && (isStatic || !defined.contains(key));
  }

  private static String nameOf(final SyntaxNode method) {
    return method.children().stream()
        .filter(part -> IDENTIFIER.equals(part.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static int arity(final SyntaxNode method) {
    return (int)
        method.children().stream()
            .filter(part -> "formalParameters".equals(part.kind()))
            .flatMap(parts -> SyntaxWalk.descendants(parts).stream())
            .filter(node -> "formalParameter".equals(node.kind()))
            .count();
  }
}
