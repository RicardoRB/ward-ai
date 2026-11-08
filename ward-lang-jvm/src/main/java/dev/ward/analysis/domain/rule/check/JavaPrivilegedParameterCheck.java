package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of an overridable method on a parameter of a public method of a public class when
 * the same method is called on the same parameter inside a {@code doPrivileged} action, as
 * SpotBugs' USC_POTENTIAL_SECURITY_CHECK_BASED_ON_UNTRUSTED_SOURCE does: a subclass passed in by
 * the caller can answer the check differently from the privileged use. The method must be known to
 * be non-final and to return a value; a call whose target is unknown is never reported.
 */
public final class JavaPrivilegedParameterCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method
   */
  public JavaPrivilegedParameterCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.CALLS_AND_TYPES;
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
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      if (JavaNodes.modifiers(visit).contains("public")) {
        ClassShapes.methods(visit.node()).stream()
            .filter(JavaPrivilegedParameterCheck::publicInstance)
            .forEach(method -> matches.addAll(report(method, types)));
      }
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> report(final SyntaxNode member, final UnitTypeFacts types) {
    final SyntaxNode method = ClassMembers.declarationOf(member);
    final Set<String> parameters = ParameterUses.parameters(method);
    final List<SyntaxNode> actions =
        SyntaxWalk.descendants(method).stream()
            .filter(JavaPrivilegedParameterCheck::isPrivilegedCall)
            .toList();
    final List<ParameterUses.Use> inside =
        actions.stream()
            .flatMap(action -> ParameterUses.in(action, parameters, types).stream())
            .toList();
    return ParameterUses.in(method, parameters, types).stream()
        .filter(use -> actions.stream().noneMatch(action -> contains(action, use.node())))
        .filter(use -> inside.stream().anyMatch(use::sameCall))
        .map(use -> CheckSupport.at(use.node().span(), CheckSupport.fill(message, use.name(), "")))
        .toList();
  }

  private static boolean contains(final SyntaxNode outer, final SyntaxNode inner) {
    return outer.span().startOffset() <= inner.span().startOffset()
        && inner.span().startOffset() < outer.span().endOffset();
  }

  private static boolean publicInstance(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return modifiers.contains("public") && !modifiers.contains("static");
  }

  private static boolean isPrivilegedCall(final SyntaxNode node) {
    return "methodCall".equals(node.kind())
        && JavaNodes.kid(node, "identifier")
            .map(SyntaxWalk::text)
            .filter("doPrivileged"::equals)
            .isPresent();
  }
}
