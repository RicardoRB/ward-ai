package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a class with high complexity, high access to the data of other classes, and low tight
 * class cohesion, as PMD's god class does: WMC at least 47, ATFD above 5, and TCC below one third.
 * A foreign access needs the type of its receiver, so an unknown receiver counts as nothing.
 */
public final class GodClassCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final int ACCESS_PARTS = 3;
  private static final String METHOD_CALL = "methodCall";
  private static final String DOT = ".";
  private static final String THIS = "this";
  private static final String SUPER = "super";
  private static final String GETTER = "get";
  private static final String BOOLEAN_GETTER = "is";
  private static final String SETTER = "set";
  private static final String IDENTIFIER = "identifier";
  private static final int WMC_VERY_HIGH = 47;
  private static final int FEW_ATFD = 5;
  private static final double TCC_LIMIT = 1.0 / 3.0;
  private static final int PERCENT = 100;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the class; {@code {value}} is the metrics
   */
  public GodClassCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .flatMap(visit -> reportOf(visit.node(), types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode clazz, final UnitTypeFacts types) {
    final int wmc = weightedMethods(clazz);
    final int atfd = foreignAccesses(clazz, types);
    final double tcc = ClassCohesion.of(clazz);
    if (wmc < WMC_VERY_HIGH || atfd <= FEW_ATFD || tcc >= TCC_LIMIT) {
      return List.of();
    }
    final String metrics =
        "WMC " + wmc + ", ATFD " + atfd + ", TCC " + Math.round(tcc * PERCENT) + "%";
    return List.of(CheckSupport.at(clazz.span(), CheckSupport.fill(message, metrics, "")));
  }

  /** Returns the sum of the complexity of the methods and constructors of a class. */
  private static int weightedMethods(final SyntaxNode clazz) {
    return ClassMembers.callableMembers(clazz).stream()
        .mapToInt(member -> ClassMetrics.complexity(ClassMembers.declarationOf(member)))
        .sum();
  }

  /** Counts the accesses to fields, and to getters and setters, of objects of other classes. */
  private static int foreignAccesses(final SyntaxNode clazz, final UnitTypeFacts types) {
    final String own = ClassMembers.nameOf(clazz);
    return (int)
        ClassMembers.members(clazz).stream()
            .flatMap(member -> SyntaxWalk.visits(member, Set.of(EXPRESSION)).stream())
            .map(SyntaxWalk.Visit::node)
            .filter(node -> isForeignAccess(node, own, types))
            .count();
  }

  private static boolean isForeignAccess(
      final SyntaxNode expression, final String own, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = expression.children();
    if (parts.size() < ACCESS_PARTS
        || !EXPRESSION.equals(parts.get(0).kind())
        || !isDot(parts.get(1))) {
      return false;
    }
    final SyntaxNode receiver = parts.get(0);
    final String text = SyntaxWalk.text(receiver);
    if (THIS.equals(text) || SUPER.equals(text)) {
      return false;
    }
    final boolean foreign =
        types
            .typeOf(receiver.span())
            .map(TypeRef::name)
            .filter(name -> isForeign(name, own))
            .isPresent();
    return foreign && isDataAccess(parts.get(2));
  }

  private static boolean isForeign(final String name, final String own) {
    return !ClassMembers.simpleName(name).equals(own);
  }

  private static boolean isDataAccess(final SyntaxNode member) {
    if (IDENTIFIER.equals(member.kind())) {
      return true;
    }
    if (!METHOD_CALL.equals(member.kind())) {
      return false;
    }
    final String name = ClassMetrics.identifiers(member).stream().findFirst().orElse("");
    return name.startsWith(GETTER) || name.startsWith(BOOLEAN_GETTER) || name.startsWith(SETTER);
  }

  private static boolean isDot(final SyntaxNode part) {
    return DOT.equals(SyntaxWalk.text(part));
  }
}
