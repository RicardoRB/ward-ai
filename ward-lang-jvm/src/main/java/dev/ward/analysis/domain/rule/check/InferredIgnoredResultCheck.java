package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports an expression statement that drops the result of a method of this file that looks like it
 * computes a new value: it returns its own class and writes no field of it, as SpotBugs infers from
 * the method body. A method with a second overload that writes a field, or a call whose target is
 * unknown, is never reported.
 */
public final class InferredIgnoredResultCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final int STATEMENT_PARTS = 2;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the statement
   */
  public InferredIgnoredResultCheck(final String message) {
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
      final UnitTypeFacts facts,
      final SourceUnit unit) {
    final Map<String, Set<String>> pure = pureMethods(root);
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(statement -> dropsPure(statement, facts, pure))
        .map(statement -> CheckSupport.at(statement.span(), message))
        .toList();
  }

  /** The names of the methods, per class, that return the class and write none of its fields. */
  private static Map<String, Set<String>> pureMethods(final SyntaxNode root) {
    final Map<String, Set<String>> pure = new HashMap<>();
    SyntaxWalk.visits(root, Set.of("classDeclaration"))
        .forEach(
            visit ->
                pure.merge(ClassMembers.nameOf(visit.node()), pureIn(visit.node()), (l, r) -> l));
    return pure;
  }

  private static Set<String> pureIn(final SyntaxNode clazz) {
    final String name = ClassMembers.nameOf(clazz);
    final Set<String> names =
        ClassMembers.callableMembers(clazz).stream()
            .map(ClassMembers::declarationOf)
            .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
            .filter(
                declaration -> ClassMembers.typeText(declaration).filter(name::equals).isPresent())
            .map(MemberDeclarations::name)
            .collect(Collectors.toCollection(HashSet::new));
    FieldAccesses.of(clazz).members().stream()
        .filter(
            member -> member.accesses().stream().anyMatch(a -> a.use() != FieldAccesses.Use.READ))
        .forEach(member -> names.remove(member.name()));
    return names;
  }

  private static boolean dropsPure(
      final SyntaxNode statement, final UnitTypeFacts facts, final Map<String, Set<String>> pure) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.size() != STATEMENT_PARTS || !EXPRESSION.equals(parts.get(0).kind())) {
      return false;
    }
    return facts
        .callTarget(parts.get(0).span())
        .filter(call -> returnsDeclaring(call, pure))
        .isPresent();
  }

  private static boolean returnsDeclaring(
      final CallTarget call, final Map<String, Set<String>> pure) {
    final String owner = simple(call.declaringType().name());
    return call.returnType() != null
        && owner.equals(simple(call.returnType().name()))
        && pure.getOrDefault(owner, Set.of()).contains(call.name());
  }

  private static String simple(final String name) {
    return name.substring(name.lastIndexOf('.') + 1);
  }
}
