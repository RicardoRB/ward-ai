package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ImportOrigin;
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
 * Declares the names a Python statement binds: assignment targets, definitions, parameters, imports
 * and {@code global} or {@code nonlocal} aliases. An assignment declares a name once per scope; a
 * definition or import declares it each time, so a redefinition is visible to rules.
 */
final class PythonBindings {

  private static final String STAR = "*";
  private static final String DOT = ".";
  private static final String AS = "as";
  private static final String NAME = "name";
  private static final String DOTTED_NAME = "dotted_name";

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
    if (NAME.equals(inner.kind())) {
      assign(inner, destructured);
    } else if (PythonNames.isList(inner)) {
      PythonNames.elements(inner).forEach(element -> target(element, true));
    } else if ("star_expr".equals(inner.kind())) {
      target(inner.children().get(1), true);
    } else if (isBracketed(inner)) {
      target(inner.children().get(1), destructured);
    } else {
      expression.accept(inner);
    }
  }

  private static boolean isBracketed(final SyntaxNode atom) {
    return "atom".equals(atom.kind())
        && atom.children().size() == 3
        && ("(".equals(PythonSyntax.text(atom.children().get(0)))
            || "[".equals(PythonSyntax.text(atom.children().get(0))));
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

  /** Declares a function, class or parameter name in the current scope. */
  void define(final SyntaxNode name, final BindingKind kind) {
    builder.declare(
        PythonNames.identifier(name), kind, name.span(), flags(PythonNames.identifier(name)));
  }

  private Set<BindingFlag> flags(final String name) {
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (builder.current() == UnitScopes.MODULE_ID && exported.contains(name)) {
      flags.add(BindingFlag.EXPORTED);
    }
    return flags;
  }

  /** Collects the parameter names of a parameter list, visiting defaults and annotations. */
  List<SyntaxNode> parameters(final Optional<SyntaxNode> list) {
    final List<SyntaxNode> names = new ArrayList<>();
    for (final SyntaxNode child : list.map(SyntaxNode::children).orElse(List.of())) {
      if ("tfpdef".equals(child.kind()) || "vfpdef".equals(child.kind())) {
        names.add(child.children().get(0));
        child.children().stream().skip(2).forEach(expression);
      } else if ("test".equals(child.kind())) {
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
    for (final SyntaxNode name : PythonSyntax.children(statement, NAME)) {
      builder.declare(
          PythonNames.identifier(name), BindingKind.VARIABLE, name.span(), Set.of(flag));
    }
  }

  /** Declares the names of an {@code import} statement; a star import opens the module. */
  void imports(final SyntaxNode statement) {
    if ("import_name".equals(statement.kind())) {
      importName(statement);
    } else {
      importFrom(statement);
    }
  }

  private void importName(final SyntaxNode statement) {
    PythonSyntax.children(statement, "dotted_as_names").stream()
        .flatMap(names -> PythonSyntax.children(names, "dotted_as_name").stream())
        .forEach(this::dottedAlias);
  }

  private void dottedAlias(final SyntaxNode alias) {
    final SyntaxNode module = PythonSyntax.children(alias, DOTTED_NAME).get(0);
    final List<SyntaxNode> aliased = PythonSyntax.children(alias, NAME);
    final SyntaxNode bound =
        aliased.isEmpty() ? PythonSyntax.children(module, NAME).get(0) : aliased.get(0);
    declareImport(bound, PythonSyntax.dottedName(module), "");
  }

  private void importFrom(final SyntaxNode statement) {
    final String module = fromModule(statement);
    if (statement.children().stream().anyMatch(child -> STAR.equals(PythonSyntax.text(child)))) {
      builder.markOpen(builder.current());
      return;
    }
    PythonSyntax.children(statement, "import_as_names").stream()
        .flatMap(names -> PythonSyntax.children(names, "import_as_name").stream())
        .forEach(alias -> fromAlias(alias, module));
  }

  private void fromAlias(final SyntaxNode alias, final String module) {
    final List<SyntaxNode> names = PythonSyntax.children(alias, NAME);
    final String member = PythonNames.identifier(names.get(0));
    declareImport(names.get(names.size() - 1), module, member);
  }

  /** The module of a {@code from} import as written, with its leading dots. */
  private static String fromModule(final SyntaxNode statement) {
    final StringBuilder module = new StringBuilder();
    for (final SyntaxNode child : statement.children()) {
      final String text = PythonSyntax.text(child);
      if (text.startsWith(DOT)) {
        module.append(text);
      } else if (DOTTED_NAME.equals(child.kind())) {
        module.append(PythonSyntax.dottedName(child));
      } else if ("import".equals(text)) {
        break;
      }
    }
    return module.toString();
  }

  private void declareImport(final SyntaxNode bound, final String module, final String member) {
    final String identifier = PythonNames.identifier(bound);
    final Span span = bound.span();
    builder.declareImport(identifier, span, new ImportOrigin(module, member), flags(identifier));
    builder.reference(identifier, span, ReferenceAccess.WRITE);
  }

  /** Reports whether {@code token} is the keyword {@code as}. */
  static boolean isAs(final SyntaxNode token) {
    return AS.equals(PythonSyntax.text(token));
  }
}
