package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Declares the names a Python statement binds: assignment targets, definitions, parameters and
 * {@code global} or {@code nonlocal} aliases. An assignment declares a name once per scope; a
 * definition declares it each time, so a redefinition is visible to rules.
 */
final class PythonBindings {

  private static final String STAR_EXPR = "star_expr";
  private static final String ATOM = "atom";
  private static final String PARENTHESIS = "(";
  private static final String BRACKET = "[";
  private static final String TYPED = "tfpdef";
  private static final String UNTYPED = "vfpdef";
  private static final int BRACKETED_PARTS = 3;
  private static final int ANNOTATION = 2;
  private static final Set<ScopeKind> NAMED_EXPRESSION_SCOPES =
      Set.of(ScopeKind.MODULE, ScopeKind.FUNCTION, ScopeKind.LAMBDA, ScopeKind.CLASS);

  private final ScopeBuilder builder;
  private final Set<String> exported;
  private final Consumer<SyntaxNode> expression;

  PythonBindings(
      final ScopeBuilder builder,
      final Set<String> exported,
      final Consumer<SyntaxNode> expression) {
    this.builder = builder;
    this.exported = exported;
    this.expression = expression;
  }

  /** Declares the targets of an assignment, a loop header or a {@code with} item. */
  void target(final SyntaxNode node, final boolean destructured) {
    final SyntaxNode inner = PythonNames.unwrap(node);
    if (PythonNames.NAME.equals(inner.kind())) {
      assign(inner, destructured);
    } else if (PythonNames.isList(inner)) {
      PythonNames.elements(inner).forEach(element -> target(element, true));
    } else if (STAR_EXPR.equals(inner.kind())) {
      target(inner.children().get(1), true);
    } else if (isBracketed(inner)) {
      target(inner.children().get(1), destructured);
    } else {
      expression.accept(inner);
    }
  }

  private static boolean isBracketed(final SyntaxNode atom) {
    final String open = PythonSyntax.text(atom.children().get(0));
    return ATOM.equals(atom.kind())
        && atom.children().size() == BRACKETED_PARTS
        && (PARENTHESIS.equals(open) || BRACKET.equals(open));
  }

  /** Assigns {@code name}: declares it in this scope unless a binding or alias covers it. */
  void assign(final SyntaxNode name, final boolean destructured) {
    final String identifier = PythonNames.identifier(name);
    final Optional<Binding> here = builder.declared(builder.current(), identifier);
    if (here.isPresent() && here.get().has(BindingFlag.GLOBAL)) {
      if (!builder.declares(UnitScopes.MODULE_ID, identifier)) {
        builder.declareIn(
            UnitScopes.MODULE_ID, identifier, BindingKind.VARIABLE, name.span(), Set.of());
      }
    } else if (here.isEmpty()) {
      final Set<BindingFlag> flags = flags(identifier);
      if (destructured) {
        flags.add(BindingFlag.DESTRUCTURED);
      }
      builder.declare(identifier, BindingKind.VARIABLE, name.span(), flags);
    }
    builder.reference(identifier, name.span(), ReferenceAccess.WRITE);
  }

  /**
   * Binds the target of an assignment expression. PEP 572 binds it in the nearest scope that is not
   * a comprehension, so inside a comprehension the name lands in the enclosing scope.
   */
  void walrus(final SyntaxNode name) {
    final int scope = builder.nearest(NAMED_EXPRESSION_SCOPES);
    if (scope == builder.current()) {
      assign(name, false);
      return;
    }
    final String identifier = PythonNames.identifier(name);
    if (!builder.declares(scope, identifier)) {
      builder.declareIn(
          scope, identifier, BindingKind.VARIABLE, name.span(), flagsIn(scope, identifier));
    }
    builder.referenceIn(scope, identifier, name.span(), ReferenceAccess.WRITE);
  }

  /** Declares a function, class or parameter name in the current scope. */
  void define(final SyntaxNode name, final BindingKind kind) {
    final String identifier = PythonNames.identifier(name);
    builder.declare(identifier, kind, name.span(), flags(identifier));
  }

  private Set<BindingFlag> flags(final String name) {
    return flagsIn(builder.current(), name);
  }

  private Set<BindingFlag> flagsIn(final int scope, final String name) {
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (scope == UnitScopes.MODULE_ID && exported.contains(name)) {
      flags.add(BindingFlag.EXPORTED);
    }
    return flags;
  }

  /** Collects the parameter names of a parameter list, visiting defaults and annotations. */
  List<SyntaxNode> parameters(final Optional<SyntaxNode> list) {
    final List<SyntaxNode> names = new ArrayList<>();
    for (final SyntaxNode child : list.map(SyntaxNode::children).orElse(List.of())) {
      if (TYPED.equals(child.kind()) || UNTYPED.equals(child.kind())) {
        names.add(child.children().get(0));
        child.children().stream().skip(ANNOTATION).forEach(expression);
      } else if (PythonNames.TEST.equals(child.kind())) {
        expression.accept(child);
      }
    }
    return names;
  }

  /** Declares the collected parameters in the current scope. */
  void declareParameters(final List<SyntaxNode> names) {
    names.forEach(name -> define(name, BindingKind.PARAMETER));
  }

  /** Records {@code global x} or {@code nonlocal x} as an alias binding in a function. */
  void alias(final SyntaxNode statement, final BindingFlag flag) {
    if (builder.kindOf(builder.current()) == ScopeKind.MODULE) {
      return;
    }
    for (final SyntaxNode name : PythonSyntax.children(statement, PythonNames.NAME)) {
      builder.declare(
          PythonNames.identifier(name), BindingKind.VARIABLE, name.span(), Set.of(flag));
    }
  }
}
