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
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compares what a class declares with what it inherits, from the member facts of the class: a field
 * that hides an inherited field, a static method that hides an inherited static method, a method
 * whose name differs from another visible method only by case, and a method of an adapter class
 * that overloads an adapter method instead of overriding it. It reports nothing when the inherited
 * members are unknown.
 */
public final class InheritedMemberCheck implements RuleCheck {

  /** What the check looks for. */
  public enum Mode {
    /** A field with the name of an inherited field. */
    FIELD_MASKS,
    /** A static method with the signature of an inherited static method. */
    STATIC_HIDING,
    /** A method whose name differs from another visible method only in case. */
    CASE_CONFUSION,
    /** A method of an {@code *Adapter} subclass that overloads an adapter method. */
    ADAPTER_OVERLOAD
  }

  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String ADAPTER = "Adapter";
  private static final TypeMembers UNKNOWN =
      new TypeMembers(List.of(), List.of(), List.of(), List.of());
  private static final FactNeed NEED =
      new FactNeed(Set.of(CLASS_DECLARATION), Set.of(FactKind.MEMBERS));

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at each finding
   */
  public InheritedMemberCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION))) {
      final TypeMembers inherited = types.membersOf(visit.node().span()).orElse(UNKNOWN);
      switch (mode) {
        case FIELD_MASKS -> fieldMasks(visit.node(), inherited, matches);
        case STATIC_HIDING -> staticHiding(visit.node(), inherited, matches);
        case CASE_CONFUSION -> caseConfusion(visit.node(), inherited, matches);
        default -> adapterOverload(visit.node(), inherited, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void fieldMasks(
      final SyntaxNode type, final TypeMembers inherited, final List<RuleMatch> matches) {
    final Set<String> names = new HashSet<>();
    inherited.inheritedFields().forEach(field -> names.add(field.name()));
    for (final FieldAccesses.Field field : FieldAccesses.of(type).fields()) {
      if (names.contains(field.name())) {
        matches.add(CheckSupport.at(field.declarator().span(), message));
      }
    }
  }

  private void staticHiding(
      final SyntaxNode type, final TypeMembers inherited, final List<RuleMatch> matches) {
    for (final DeclaredMethods.Method method : DeclaredMethods.of(type)) {
      if (method.isStatic()
          && inherited.inheritedMethods().stream().anyMatch(i -> hides(method, i))) {
        matches.add(CheckSupport.at(method.node().span(), message));
      }
    }
  }

  private void caseConfusion(
      final SyntaxNode type, final TypeMembers inherited, final List<RuleMatch> matches) {
    final List<String> visible = new ArrayList<>();
    inherited.inheritedMethods().forEach(method -> visible.add(method.name()));
    for (final DeclaredMethods.Method method : DeclaredMethods.of(type)) {
      if (visible.stream()
          .anyMatch(name -> name.equalsIgnoreCase(method.name()) && !name.equals(method.name()))) {
        matches.add(CheckSupport.at(method.node().span(), message));
      }
      visible.add(method.name());
    }
  }

  private void adapterOverload(
      final SyntaxNode type, final TypeMembers inherited, final List<RuleMatch> matches) {
    final boolean adapter =
        inherited.ancestors().stream().anyMatch(ancestor -> ancestor.name().endsWith(ADAPTER));
    if (!adapter) {
      return;
    }
    for (final DeclaredMethods.Method method : DeclaredMethods.of(type)) {
      final List<CallTarget> sameName =
          inherited.inheritedMethods().stream()
              .filter(i -> i.name().equals(method.name()))
              .toList();
      if (!sameName.isEmpty() && sameName.stream().noneMatch(i -> sameSignature(method, i))) {
        matches.add(CheckSupport.at(method.node().span(), message));
      }
    }
  }

  private static boolean hides(final DeclaredMethods.Method method, final CallTarget inherited) {
    return inherited.isStatic()
        && inherited.name().equals(method.name())
        && sameSignature(method, inherited);
  }

  private static boolean sameSignature(
      final DeclaredMethods.Method method, final CallTarget inherited) {
    return inherited.parameters().stream()
        .map(InheritedMemberCheck::simple)
        .toList()
        .equals(method.parameterTypes());
  }

  private static String simple(final TypeRef type) {
    final String name = type.name();
    return name.substring(name.lastIndexOf('.') + 1) + "[]".repeat(type.arrayDepth());
  }
}
