package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a Python syntax tree and records its scopes. Python resolves a name with the LEGB order:
 * local, enclosing functions, module, built-ins. A class body is a scope that its methods skip; a
 * comprehension has a scope of its own whose first iterable is evaluated outside it; {@code global}
 * and {@code nonlocal} send an assignment past the function's own scope.
 */
final class PythonScopeWalk {

  private static final int TRIPLE = 3;

  /** Names that can create or read names the walk cannot see. */
  private static final Set<String> OPENS_SCOPE = Set.of("exec", "eval", "locals", "vars");

  private static final String NAME = "name";
  private static final String IN = "in";

  private final ScopeBuilder builder;
  private final PythonBindings bindings;
  private final Map<String, Consumer<SyntaxNode>> handlers = new HashMap<>();

  PythonScopeWalk(final ScopeBuilder builder, final Set<String> exported) {
    this.builder = builder;
    this.bindings = new PythonBindings(builder, exported, this::visit);
    handlers.put(NAME, this::read);
    handlers.put("funcdef", this::function);
    handlers.put("classdef", this::classDefinition);
    handlers.put("lambdef", this::lambda);
    handlers.put("expr_stmt", this::assignment);
    handlers.put("for_stmt", this::forLoop);
    handlers.put("with_item", this::withItem);
    handlers.put("except_clause", this::exceptClause);
    handlers.put("import_name", bindings::imports);
    handlers.put("import_from", bindings::imports);
    handlers.put("global_stmt", statement -> bindings.alias(statement, BindingFlag.GLOBAL));
    handlers.put("nonlocal_stmt", statement -> bindings.alias(statement, BindingFlag.NONLOCAL));
    handlers.put("del_stmt", this::delete);
    handlers.put("trailer", this::trailer);
    handlers.put("argument", this::argument);
    handlers.put("decorator", this::decorator);
    handlers.put("atom", this::atom);
  }

  /** Visits {@code node} and everything under it. */
  void visit(final SyntaxNode node) {
    handlers.getOrDefault(node.kind(), this::generic).accept(node);
  }

  private void generic(final SyntaxNode node) {
    if (PythonSyntax.children(node, "comp_for").isEmpty()) {
      node.children().forEach(this::visit);
    } else {
      comprehension(node);
    }
  }

  private void read(final SyntaxNode name) {
    final String identifier = PythonNames.identifier(name);
    builder.reference(identifier, name.span(), ReferenceAccess.READ);
    if (OPENS_SCOPE.contains(identifier)) {
      builder.markOpen(builder.current());
    } else if ("globals".equals(identifier)) {
      builder.markOpen(UnitScopes.MODULE_ID);
    }
  }

  private void function(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final List<SyntaxNode> parameters =
        bindings.parameters(
            PythonSyntax.children(parts.get(2), "typedargslist").stream().findFirst());
    final int arrow = indexOf(parts, "->");
    if (arrow >= 0) {
      visit(parts.get(arrow + 1));
    }
    bindings.define(parts.get(1), BindingKind.FUNCTION);
    builder.open(ScopeKind.FUNCTION, node.span());
    bindings.declareParameters(parameters);
    visit(parts.get(parts.size() - 1));
    builder.close();
  }

  private void classDefinition(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    PythonSyntax.children(node, "arglist").forEach(this::visit);
    bindings.define(parts.get(1), BindingKind.CLASS);
    builder.open(ScopeKind.CLASS, node.span());
    visit(parts.get(parts.size() - 1));
    builder.close();
  }

  private void lambda(final SyntaxNode node) {
    final List<SyntaxNode> parameters =
        bindings.parameters(PythonSyntax.children(node, "varargslist").stream().findFirst());
    builder.open(ScopeKind.LAMBDA, node.span());
    bindings.declareParameters(parameters);
    visit(node.children().get(node.children().size() - 1));
    builder.close();
  }

  private void assignment(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 1) {
      visit(parts.get(0));
    } else if ("annassign".equals(parts.get(1).kind())) {
      annotated(parts.get(0), parts.get(1));
    } else if ("augassign".equals(parts.get(1).kind())) {
      augmented(parts);
    } else {
      visit(parts.get(parts.size() - 1));
      for (int index = 0; index < parts.size() - 1; index += 2) {
        bindings.target(parts.get(index), false);
      }
    }
  }

  private void annotated(final SyntaxNode target, final SyntaxNode annotation) {
    annotation.children().stream()
        .filter(child -> "test".equals(child.kind()))
        .forEach(this::visit);
    PythonNames.plainName(target)
        .ifPresentOrElse(name -> bindings.assign(name, false), () -> visit(target));
  }

  private void augmented(final List<SyntaxNode> parts) {
    visit(parts.get(parts.size() - 1));
    visit(parts.get(0));
    PythonNames.plainName(parts.get(0)).ifPresent(name -> bindings.assign(name, false));
  }

  private void forLoop(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final int in = indexOf(parts, IN);
    visit(parts.get(in + 1));
    bindings.target(parts.get(in - 1), true);
    parts.stream().skip(in + 2).forEach(this::visit);
  }

  private void withItem(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    visit(parts.get(0));
    if (parts.size() > 2) {
      bindings.target(parts.get(2), false);
    }
  }

  private void exceptClause(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    parts.stream().filter(part -> "test".equals(part.kind())).forEach(this::visit);
    PythonSyntax.children(node, NAME).forEach(name -> bindings.assign(name, false));
  }

  private void delete(final SyntaxNode node) {
    final SyntaxNode targets = PythonNames.unwrap(node.children().get(1));
    final List<SyntaxNode> elements =
        PythonNames.isList(targets) ? PythonNames.elements(targets) : List.of(targets);
    for (final SyntaxNode element : elements) {
      final Optional<SyntaxNode> name = PythonNames.plainName(element);
      if (name.isPresent()) {
        builder.reference(
            PythonNames.identifier(name.get()), name.get().span(), ReferenceAccess.DELETE);
      } else {
        visit(element);
      }
    }
  }

  private void trailer(final SyntaxNode node) {
    if (!".".equals(PythonSyntax.text(node.children().get(0)))) {
      node.children().forEach(this::visit);
    }
  }

  private void argument(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == TRIPLE && "=".equals(PythonSyntax.text(parts.get(1)))) {
      visit(parts.get(2));
    } else {
      generic(node);
    }
  }

  private void decorator(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if ("dotted_name".equals(child.kind())) {
        read(PythonSyntax.children(child, NAME).get(0));
      } else {
        visit(child);
      }
    }
  }

  private void atom(final SyntaxNode node) {
    final List<SyntaxNode> strings =
        node.children().stream().filter(child -> "STRING".equals(child.kind())).toList();
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
                        PythonFStrings.within(string.span(), text, use.from(), use.to()),
                        ReferenceAccess.READ));
      }
    }
  }

  private void comprehension(final SyntaxNode node) {
    final SyntaxNode first = PythonSyntax.children(node, "comp_for").get(0);
    visit(first.children().get(indexOf(first.children(), IN) + 1));
    builder.open(ScopeKind.COMPREHENSION, node.span());
    clause(first, true);
    node.children().stream().filter(child -> child != first).forEach(this::visit);
    builder.close();
  }

  private void clause(final SyntaxNode clause, final boolean first) {
    final List<SyntaxNode> parts = clause.children();
    if ("comp_for".equals(clause.kind())) {
      bindings.target(parts.get(indexOf(parts, "for") + 1), true);
      if (!first) {
        visit(parts.get(indexOf(parts, IN) + 1));
      }
    } else if ("comp_if".equals(clause.kind())) {
      visit(parts.get(1));
    }
    parts.stream()
        .filter(part -> "comp_iter".equals(part.kind()))
        .forEach(next -> clause(next.children().get(0), false));
  }

  private static int indexOf(final List<SyntaxNode> parts, final String text) {
    for (int index = 0; index < parts.size(); index++) {
      if (text.equals(PythonSyntax.text(parts.get(index)))) {
        return index;
      }
    }
    return -1;
  }
}
