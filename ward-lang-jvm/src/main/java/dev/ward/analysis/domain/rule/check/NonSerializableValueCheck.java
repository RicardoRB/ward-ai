package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a value that is known not to be serializable where a serializable one is needed. In
 * {@code field} mode it is stored in a non-transient instance field of a serializable class; in
 * {@code session} mode it is stored in an HTTP session, which the container may serialize. A value
 * counts as not serializable when its type is a known concrete class with known supertypes, none of
 * which is Serializable; any type that is not fully known is let through.
 */
public final class NonSerializableValueCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String SERIALIZABLE = "java.io.Serializable";
  private static final String TRANSIENT = "transient";
  private static final Set<String> SESSIONS =
      Set.of("javax.servlet.http.HttpSession", "jakarta.servlet.http.HttpSession");
  private static final Set<String> STORES = Set.of("setAttribute", "putValue");
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(EXPRESSION),
          Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS, FactKind.SHAPE));

  private final boolean session;
  private final String message;

  /**
   * Creates the check.
   *
   * @param session whether to look at stores into an HTTP session, instead of into fields
   * @param message the message; {@code {value}} is the type of the value
   */
  public NonSerializableValueCheck(final boolean session, final String message) {
    this.session = session;
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
    if (session) {
      sessionStores(root, types, matches);
    } else {
      fieldStores(root, types, matches);
    }
    return List.copyOf(matches);
  }

  private void sessionStores(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> matches) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final Optional<CallTarget> target = types.callTarget(visit.node().span());
      final List<SyntaxNode> arguments = CallArgumentShapes.arguments(visit.node());
      if (target.isPresent()
          && SESSIONS.contains(target.get().declaringType().name())
          && STORES.contains(target.get().name())
          && arguments.size() == 2
          && notSerializable(arguments.get(1).span(), types)) {
        matches.add(report(arguments.get(1), types));
      }
    }
  }

  private void fieldStores(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> matches) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      final SyntaxNode clazz = visit.node();
      if (!ClassHeaders.implementsSerializable(clazz)) {
        continue;
      }
      final Set<String> fields = serializedFields(clazz);
      for (final SyntaxNode assignment : Assignments.in(clazz)) {
        final String target = Assignments.target(assignment);
        final String field =
            target.startsWith("this.") ? target.substring("this.".length()) : target;
        final SyntaxNode value = assignment.children().get(2);
        if (fields.contains(field) && notSerializable(value.span(), types)) {
          matches.add(report(value, types));
        }
      }
    }
  }

  private static Set<String> serializedFields(final SyntaxNode clazz) {
    final Set<String> names = new java.util.HashSet<>();
    for (final SyntaxNode field : ClassMembers.fieldMembers(clazz)) {
      final List<String> modifiers = MemberDeclarations.modifiers(field);
      if (!modifiers.contains(TRANSIENT) && !modifiers.contains(MemberDeclarations.STATIC)) {
        names.addAll(ClassMembers.fieldNames(ClassMembers.declarationOf(field)));
      }
    }
    return names;
  }

  private RuleMatch report(final SyntaxNode value, final UnitTypeFacts types) {
    final String type = types.typeOf(value.span()).map(TypeRef::name).orElse("");
    return CheckSupport.at(value.span(), CheckSupport.fill(message, type, ""));
  }

  private static boolean notSerializable(final Span span, final UnitTypeFacts types) {
    final Optional<TypeRef> type = types.typeOf(span);
    final Optional<TypeMembers> members = types.membersOf(span);
    final Optional<TypeShape> shape = types.shapeOf(span);
    return type.isPresent()
        && type.get().arrayDepth() == 0
        && !type.get().primitive()
        && shape
            .filter(found -> found.kind() == TypeShape.Kind.CLASS && !found.isAbstract())
            .isPresent()
        && members.filter(found -> !found.ancestors().isEmpty()).isPresent()
        && !SERIALIZABLE.equals(type.get().name())
        && members.get().ancestors().stream()
            .noneMatch(ancestor -> SERIALIZABLE.equals(ancestor.name()));
  }
}
