package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a Python syntax tree and records its scopes. Python resolves a name with the LEGB order:
 * local, enclosing functions, module, built-ins. A class body is a scope that its methods skip; a
 * comprehension has a scope of its own whose first iterable is evaluated outside it; {@code global}
 * and {@code nonlocal} send an assignment past the function's own scope. This class dispatches by
 * node kind and reads expressions; {@link PythonDefinitions} and {@link PythonStatements} handle
 * the nodes that open scopes and bind names.
 */
final class PythonScopeWalk {

  /** Names that can create or read names the walk cannot see. */
  private static final Set<String> OPENS_SCOPE = Set.of("exec", "eval", "locals", "vars");

  private static final String GLOBALS = "globals";
  private static final String DOT = ".";
  private static final String EQUALS = "=";
  private static final String STRING = "STRING";
  private static final int KEYWORD_ARGUMENT = 3;

  private final ScopeBuilder builder;
  private final PythonDefinitions comprehensions;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();

  PythonScopeWalk(final ScopeBuilder builder, final Set<String> exported) {
    this.builder = builder;
    final PythonBindings bindings = new PythonBindings(builder, exported, this::visit);
    final PythonImports imports = new PythonImports(builder, exported);
    final PythonDefinitions definitions = new PythonDefinitions(builder, bindings, this::visit);
    final PythonStatements statements = new PythonStatements(builder, bindings, this::visit);
    handlers.put(PythonNames.NAME, this::read);
    handlers.put("funcdef", definitions::function);
    handlers.put("classdef", definitions::classDefinition);
    handlers.put("lambdef", definitions::lambda);
    handlers.put("expr_stmt", statements::assignment);
    handlers.put("namedexpr_test", statements::namedExpression);
    handlers.put("for_stmt", statements::forLoop);
    handlers.put("with_item", statements::withItem);
    handlers.put("except_clause", statements::exceptClause);
    handlers.put("del_stmt", statements::delete);
    handlers.put("import_name", imports::importName);
    handlers.put("import_from", imports::importFrom);
    handlers.put("global_stmt", statement -> bindings.alias(statement, BindingFlag.GLOBAL));
    handlers.put("nonlocal_stmt", statement -> bindings.alias(statement, BindingFlag.NONLOCAL));
    handlers.put("trailer", this::trailer);
    handlers.put("argument", this::argument);
    handlers.put("atom", this::atom);
    this.comprehensions = definitions;
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  private void generic(final SyntaxNode node) {
    if (PythonSyntax.children(node, PythonDefinitions.COMP_FOR).isEmpty()) {
      node.children().forEach(this::visit);
    } else {
      comprehensions.comprehension(node);
    }
  }

  private void read(final SyntaxNode name) {
    final String identifier = PythonNames.identifier(name);
    builder.reference(identifier, name.span(), ReferenceAccess.READ);
    if (OPENS_SCOPE.contains(identifier)) {
      builder.markOpen(builder.current());
    } else if (GLOBALS.equals(identifier)) {
      builder.markOpen(UnitScopes.MODULE_ID);
    }
  }

  private void trailer(final SyntaxNode node) {
    if (!DOT.equals(PythonSyntax.text(node.children().get(0)))) {
      node.children().forEach(this::visit);
    }
  }

  private void argument(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == KEYWORD_ARGUMENT && EQUALS.equals(PythonSyntax.text(parts.get(1)))) {
      visit(parts.get(2));
    } else {
      generic(node);
    }
  }

  private void atom(final SyntaxNode node) {
    final List<SyntaxNode> strings =
        node.children().stream().filter(child -> STRING.equals(child.kind())).toList();
    if (strings.isEmpty()) {
      generic(node);
    }
    for (final SyntaxNode string : strings) {
      final String text = PythonSyntax.text(string);
      if (PythonFStrings.isFString(text)) {
        PythonFStrings.names(text)
            .forEach(
                use ->
                    builder.reference(
                        use.name(),
                        PythonSpans.within(string.span(), text, use.from(), use.to()),
                        ReferenceAccess.READ));
      }
    }
  }
}
