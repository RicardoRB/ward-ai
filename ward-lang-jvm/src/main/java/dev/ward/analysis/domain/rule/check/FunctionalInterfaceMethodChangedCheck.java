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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code default} method of a {@code @FunctionalInterface} that overrides the abstract
 * method of a functional superinterface with more than a call to this interface's own abstract
 * method. A lambda cast to the sub-interface then behaves differently from the same lambda cast to
 * the superinterface. It reports nothing when the interface does not declare exactly one abstract
 * method, or when the facts about the superinterfaces or the overridden method are unknown.
 */
public final class FunctionalInterfaceMethodChangedCheck implements RuleCheck {

  private static final String FUNCTIONAL = "FunctionalInterface";
  private static final String INTERFACE = "interfaceDeclaration";
  private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");
  private static final Set<String> FACT_NODES =
      Set.of(
          "interfaceMethodDeclaration",
          "interfaceMemberDeclaration",
          "interfaceBodyDeclaration",
          "typeType",
          "expression");
  private static final FactNeed NEED =
      new FactNeed(
          FACT_NODES, Set.of(FactKind.OVERRIDES, FactKind.MEMBERS, FactKind.TYPE, FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method
   */
  public FunctionalInterfaceMethodChangedCheck(final String message) {
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(INTERFACE))) {
      if (functional(visit)) {
        inInterface(visit.node(), types)
            .forEach(method -> found.add(CheckSupport.at(method.span(), message)));
      }
    }
    return List.copyOf(found);
  }

  private static boolean functional(final SyntaxWalk.Visit visit) {
    for (int index = visit.ancestors().size() - 1; index >= 0; index--) {
      final SyntaxNode holder = visit.ancestors().get(index);
      if (Set.of("typeDeclaration", "classBodyDeclaration", "interfaceBodyDeclaration")
          .contains(holder.kind())) {
        return NullSyntax.annotations(holder).stream()
            .map(NullSyntax::simpleName)
            .anyMatch(FUNCTIONAL::equals);
      }
    }
    return false;
  }

  private static List<SyntaxNode> inInterface(
      final SyntaxNode declaration, final UnitTypeFacts types) {
    final Optional<InterfaceSam.Sam> sam = InterfaceSam.only(declaration);
    final Set<String> supers = functionalSupers(declaration, types);
    if (sam.isEmpty() || supers.isEmpty()) {
      return List.of();
    }
    final String own = NullOperands.methodName(declaration);
    return InterfaceSam.methods(declaration).stream()
        .filter(InterfaceSam::isDefault)
        .filter(method -> overridesSam(method, supers, types))
        .filter(method -> !DefaultBodies.preserves(method, own, sam.get(), types))
        .toList();
  }

  /** The names of the interfaces the declaration extends that are themselves functional. */
  private static Set<String> functionalSupers(
      final SyntaxNode declaration, final UnitTypeFacts types) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode type : SyntaxWalk.descendants(declaration)) {
      final boolean listed =
          "typeType".equals(type.kind())
              && NullSyntax.first(declaration, "typeList")
                  .map(list -> list.children().contains(type))
                  .orElse(false);
      if (listed && annotated(types, type)) {
        types.typeOf(type.span()).ifPresent(found -> names.add(found.name()));
      }
    }
    return names;
  }

  private static boolean annotated(final UnitTypeFacts types, final SyntaxNode type) {
    return types
        .membersOf(type.span())
        .map(TypeMembers::annotations)
        .map(all -> all.stream().anyMatch(name -> name.endsWith("." + FUNCTIONAL)))
        .orElse(false);
  }

  private static boolean overridesSam(
      final SyntaxNode method, final Set<String> supers, final UnitTypeFacts types) {
    return overridden(method, types).stream()
        .anyMatch(
            target ->
                supers.contains(target.declaringType().name())
                    && Boolean.TRUE.equals(target.isAbstract())
                    && !OBJECT_METHODS.contains(target.name()));
  }

  /**
   * The methods the declaration overrides, as the facts of the method or the nodes around it say.
   */
  private static List<CallTarget> overridden(final SyntaxNode method, final UnitTypeFacts types) {
    return types.at(method.span()).overrides();
  }
}
