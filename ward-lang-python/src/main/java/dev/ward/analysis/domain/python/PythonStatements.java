package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Handles the Python statements that bind or delete names. */
final class PythonStatements {

  private static final String IN = "in";
  private static final String ANNASSIGN = "annassign";
  private static final String AUGASSIGN = "augassign";
  private static final int TARGET_AFTER_AS = 2;
  private static final int SINGLE = 1;

  private final ScopeBuilder builder;
  private final PythonBindings bindings;
  private final Consumer<SyntaxNode> visit;

  PythonStatements(
      final ScopeBuilder builder, final PythonBindings bindings, final Consumer<SyntaxNode> visit) {
    this.builder = builder;
    this.bindings = bindings;
    this.visit = visit;
  }

  void assignment(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == SINGLE) {
      visit.accept(parts.get(0));
    } else if (ANNASSIGN.equals(parts.get(1).kind())) {
      annotated(parts.get(0), parts.get(1));
    } else if (AUGASSIGN.equals(parts.get(1).kind())) {
      augmented(parts);
    } else {
      visit.accept(parts.get(parts.size() - 1));
      for (int index = 0; index < parts.size() - 1; index += 2) {
        bindings.target(parts.get(index), false);
      }
    }
  }

  private void annotated(final SyntaxNode target, final SyntaxNode annotation) {
    annotation.children().stream()
        .filter(child -> PythonNames.TEST.equals(child.kind()))
        .forEach(visit);
    PythonNames.plainName(target)
        .ifPresentOrElse(name -> bindings.assign(name, false), () -> visit.accept(target));
  }

  private void augmented(final List<SyntaxNode> parts) {
    visit.accept(parts.get(parts.size() - 1));
    visit.accept(parts.get(0));
    PythonNames.plainName(parts.get(0)).ifPresent(name -> bindings.assign(name, false));
  }

  void forLoop(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final int in = PythonNames.indexOf(parts, IN);
    visit.accept(parts.get(in + 1));
    bindings.target(parts.get(in - 1), true);
    parts.stream().skip(in + 2).forEach(visit);
  }

  void withItem(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    visit.accept(parts.get(0));
    if (parts.size() > TARGET_AFTER_AS) {
      bindings.target(parts.get(TARGET_AFTER_AS), false);
    }
  }

  void exceptClause(final SyntaxNode node) {
    node.children().stream().filter(part -> PythonNames.TEST.equals(part.kind())).forEach(visit);
    PythonSyntax.children(node, PythonNames.NAME).forEach(name -> bindings.assign(name, false));
  }

  void delete(final SyntaxNode node) {
    final SyntaxNode targets = PythonNames.unwrap(node.children().get(1));
    final List<SyntaxNode> elements =
        PythonNames.isList(targets) ? PythonNames.elements(targets) : List.of(targets);
    for (final SyntaxNode element : elements) {
      final Optional<SyntaxNode> name = PythonNames.plainName(element);
      if (name.isPresent()) {
        builder.reference(
            PythonNames.identifier(name.get()), name.get().span(), ReferenceAccess.DELETE);
      } else {
        visit.accept(element);
      }
    }
  }
}
