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
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each parameter or local variable that has the name of a field of its enclosing class,
 * like Checkstyle's HiddenField. Fields are the class's field declarations and the parameter
 * properties of its constructor; accessors are not fields. The class members come from the tree,
 * because the scopes do not bind them. The options mirror ward:java/hidden-field.
 */
public final class NodeHiddenFieldCheck implements RuleCheck {

  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CLASS_EXPRESSION = "classExpression";
  private static final Set<ScopeKind> LOCAL_SCOPES = Set.of(ScopeKind.FUNCTION, ScopeKind.BLOCK);
  private static final Set<BindingKind> LOCAL_KINDS =
      Set.of(BindingKind.PARAMETER, BindingKind.VARIABLE, BindingKind.CONSTANT);

  private final boolean ignoreConstructorParameter;
  private final boolean ignoreSetter;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoreConstructorParameter whether a constructor parameter that hides a field is left
   *     alone
   * @param ignoreSetter whether the parameter of a setter for the field is left alone
   * @param message the message of each finding; {@code {value}} is the name
   */
  public NodeHiddenFieldCheck(
      final boolean ignoreConstructorParameter, final boolean ignoreSetter, final String message) {
    this.ignoreConstructorParameter = ignoreConstructorParameter;
    this.ignoreSetter = ignoreSetter;
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the hidden-field check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final Map<Span, SyntaxNode> classes = new HashMap<>();
    collectClasses(root, classes);
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (LOCAL_SCOPES.contains(scope.kind())) {
        for (final Binding binding : scope.bindings()) {
          if (LOCAL_KINDS.contains(binding.kind())) {
            enclosingClass(scope, scopes, classes)
                .ifPresent(declared -> consider(declared, scope, binding, found));
          }
        }
      }
    }
    found.sort(Comparator.comparingInt(match -> match.span().startOffset()));
    return List.copyOf(found);
  }

  private static void collectClasses(final SyntaxNode node, final Map<Span, SyntaxNode> classes) {
    if (CLASS_DECLARATION.equals(node.kind()) || CLASS_EXPRESSION.equals(node.kind())) {
      classes.put(node.span(), node);
    }
    node.children().forEach(child -> collectClasses(child, classes));
  }

  /** The class declaration of the nearest class scope above the scope, if the tree has it. */
  private static Optional<SyntaxNode> enclosingClass(
      final Scope scope, final UnitScopes scopes, final Map<Span, SyntaxNode> classes) {
    Optional<Scope> ancestor = scopes.parent(scope);
    while (ancestor.isPresent() && ancestor.get().kind() != ScopeKind.CLASS) {
      ancestor = scopes.parent(ancestor.get());
    }
    return ancestor.map(Scope::span).map(classes::get);
  }

  private void consider(
      final SyntaxNode declared,
      final Scope scope,
      final Binding binding,
      final List<RuleMatch> found) {
    final List<SyntaxNode> members = NodeClassMembers.elements(declared);
    if (!NodeClassMembers.fieldNames(members).contains(binding.name())) {
      return;
    }
    final Optional<SyntaxNode> member =
        members.stream()
            .filter(element -> holds(element.span(), scope.span().startOffset()))
            .findFirst();
    if (member.isPresent() && !skipped(member.get(), binding)) {
      found.add(new RuleMatch(binding.declaration(), message.replace("{value}", binding.name())));
    }
  }

  /**
   * Whether a member keeps the binding out of the findings: its own parameter property, a
   * constructor parameter, or a setter's parameter.
   */
  private boolean skipped(final SyntaxNode element, final Binding binding) {
    if (binding.kind() != BindingKind.PARAMETER) {
      return false;
    }
    return NodeClassMembers.isParameterProperty(element, binding.declaration().startOffset())
        || ignoreConstructorParameter && NodeClassMembers.isConstructor(element)
        || ignoreSetter && NodeClassMembers.isSetter(element, binding.name());
  }

  private static boolean holds(final Span span, final int offset) {
    return span.startOffset() <= offset && offset < span.endOffset();
  }
}
