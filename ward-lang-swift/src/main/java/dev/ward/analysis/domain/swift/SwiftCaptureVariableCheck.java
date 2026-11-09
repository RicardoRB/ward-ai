package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a name in a closure's capture list that resolves to a {@code var} declared at module or
 * type level, like SwiftLint's {@code capture_variable}. A {@code let}, a parameter, a local {@code
 * var} and a {@code lazy var} are not reported, and an unresolved name is never reported. An item
 * that assigns, such as {@code x = expr}, and {@code self} are skipped. shortcut: a local {@code
 * var} is never reported, because SwiftLint's examples do not report them; upgrade when the rule
 * needs local captures.
 */
public final class SwiftCaptureVariableCheck implements RuleCheck {

  private static final String CAPTURE_ITEM = "capture_list_item";
  private static final String VARIABLE = "variable_declaration";
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String LAZY = "lazy";
  private static final String ASSIGN = "=";
  private static final String SELF = "self";
  private static final Set<ScopeKind> DECLARED_OUTSIDE_FUNCTIONS =
      Set.of(ScopeKind.MODULE, ScopeKind.CLASS);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftCaptureVariableCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the resolved names, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the capture-variable check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final Set<Integer> lazyNames = lazyDeclarationOffsets(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode item : SwiftTrees.descendants(root, CAPTURE_ITEM)) {
      final List<SyntaxNode> leaves = SwiftTrees.leaves(item);
      if (leaves.isEmpty() || SwiftTrees.hasToken(item, ASSIGN)) {
        continue;
      }
      final SyntaxNode name = leaves.get(leaves.size() - 1);
      if (!SELF.equals(name.fields().get("text")) && isCapturedVariable(name, scopes, lazyNames)) {
        matches.add(SwiftTrees.at(name, message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isCapturedVariable(
      final SyntaxNode name, final UnitScopes scopes, final Set<Integer> lazyNames) {
    return resolvedAt(name, scopes)
        .filter(binding -> binding.kind() == BindingKind.VARIABLE)
        .filter(binding -> !lazyNames.contains(binding.declaration().startOffset()))
        .filter(binding -> isDeclaredOutsideFunctions(binding, scopes))
        .isPresent();
  }

  private static Optional<Binding> resolvedAt(final SyntaxNode name, final UnitScopes scopes) {
    return scopes.references().stream()
        .filter(reference -> reference.span().startOffset() == name.span().startOffset())
        .findFirst()
        .flatMap(NameReference::resolved);
  }

  private static boolean isDeclaredOutsideFunctions(
      final Binding binding, final UnitScopes scopes) {
    return scopes.scopes().stream()
        .filter(scope -> scope.id() == binding.scope())
        .map(Scope::kind)
        .anyMatch(DECLARED_OUTSIDE_FUNCTIONS::contains);
  }

  /** The offsets of the names declared by {@code lazy var} declarations. */
  private static Set<Integer> lazyDeclarationOffsets(final SyntaxNode root) {
    final Set<Integer> offsets = new HashSet<>();
    for (final SyntaxNode declaration : SwiftTrees.descendants(root, VARIABLE)) {
      if (SwiftTrees.hasToken(declaration, LAZY)) {
        SwiftTrees.descendants(declaration, IDENTIFIER)
            .forEach(identifier -> offsets.add(identifier.span().startOffset()));
      }
    }
    return offsets;
  }
}
