package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One file as the program-data analysis reads it: its tree with the parent of each node, its
 * scopes, and the binding that each name in it resolves to. The indexes are built when first read,
 * so a file the analysis never reaches costs nothing.
 */
final class NodeProgramFile {
  private static final String EXPORT = "export";

  private final SourceUnit unit;
  private final SyntaxNode root;
  private final AtomicReference<NodeProgramIndex> built = new AtomicReference<>();

  NodeProgramFile(final SourceUnit unit, final SyntaxNode root) {
    this.unit = unit;
    this.root = root;
  }

  String path() {
    return unit.path();
  }

  UnitScopes scopes() {
    return index().scopes();
  }

  /** Whether a {@code with} statement or an {@code eval} call lets names the scopes miss reach it. */
  boolean open() {
    return scopes().scopes().stream().anyMatch(Scope::open);
  }

  /** The node that holds {@code node}, or none for the root. */
  Optional<SyntaxNode> parent(final SyntaxNode node) {
    return Optional.ofNullable(index().parents().get(node));
  }

  /** The identifier token that starts at the span, such as the name a binding declares. */
  Optional<SyntaxNode> nameAt(final Span span) {
    return Optional.ofNullable(index().names().get(span.startOffset()));
  }

  /** The binding that a name expression resolves to, or none when no scope declares it. */
  Optional<Binding> bindingOf(final SyntaxNode nameToken) {
    return Optional.ofNullable(index().references().get(nameToken.span().startOffset()))
        .flatMap(NameReference::resolved);
  }

  /** Whether an {@code export} keyword or statement holds the declaration at {@code node}. */
  boolean exported(final SyntaxNode node) {
    Optional<SyntaxNode> current = Optional.of(node);
    while (current.isPresent() && !"program".equals(current.get().kind())) {
      final SyntaxNode candidate = current.get();
      if ("exportStatement".equals(candidate.kind()) || startsWithExport(candidate)) {
        return true;
      }
      current = parent(candidate);
    }
    return false;
  }

  private static boolean startsWithExport(final SyntaxNode node) {
    return !node.children().isEmpty()
        && EXPORT.equals(NodeFlowSyntax.text(node.children().get(0)))
        && node.children().get(0).children().isEmpty();
  }

  private NodeProgramIndex index() {
    return built.updateAndGet(
        current -> current == null ? NodeProgramIndex.of(unit, root) : current
    );
  }
}
