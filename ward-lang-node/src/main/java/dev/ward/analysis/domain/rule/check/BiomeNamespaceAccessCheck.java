package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports a computed access, such as {@code ns["key"]} or {@code ns[key]}, to a name that resolves
 * to a namespace import. A dotted access is not reported, and a local that shadows the import
 * resolves to its own binding, so it is not reported either.
 */
public final class BiomeNamespaceAccessCheck implements RuleCheck {

  private static final String CALL = "singleExpression";
  private static final String NAMESPACE = "*";
  private static final String OPEN_BRACKET = "'['";
  private static final String ARRAY = "arrayLiteral";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeNamespaceAccessCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check resolves names through the scopes, which only {@link
   * #matches(SyntaxNode, FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the namespace access check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final Map<Long, NameReference> byName = referencesByName(view.scopes());
    return SyntaxWalk.visits(root, Set.of(CALL)).stream()
        .filter(visit -> computedAccess(visit.node()))
        .filter(visit -> resolvesToNamespace(visit.node(), byName))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  /** The references keyed by their start and end, so a name must be exactly the accessed base. */
  private static Map<Long, NameReference> referencesByName(final UnitScopes scopes) {
    final Map<Long, NameReference> byName = new HashMap<>();
    for (final NameReference reference : scopes.references()) {
      byName.put(key(reference.span()), reference);
    }
    return byName;
  }

  private static long key(final Span span) {
    return ((long) span.startOffset() << Integer.SIZE) | span.endOffset();
  }

  /** The node indexes a value: a bracket after its base, or a name followed by an array literal. */
  private static boolean computedAccess(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    return BiomeTree.hasChild(node, OPEN_BRACKET)
        || parts.size() == 2
            && BiomeTree.isKind(parts.get(1), CALL)
            && BiomeTree.hasChild(parts.get(1), ARRAY);
  }

  private static boolean resolvesToNamespace(
      final SyntaxNode node, final Map<Long, NameReference> byName) {
    final NameReference reference = byName.get(key(node.children().get(0).span()));
    return reference != null
        && reference.resolved().filter(BiomeNamespaceAccessCheck::isNamespace).isPresent();
  }

  private static boolean isNamespace(final Binding binding) {
    return binding.kind() == BindingKind.IMPORT
        && binding.origin().map(origin -> NAMESPACE.equals(origin.member())).orElse(false);
  }
}
