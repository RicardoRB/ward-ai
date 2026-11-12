package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The outcome of reading one YAML or JSON text: the document tree and the diagnostics. A text with
 * a diagnostic has an empty {@code document} node as its tree, since no rule may run on it.
 *
 * @param tree the root of the document, see {@link YamlSyntax} for the node kinds
 * @param diagnostics the problems found; empty when the text was read completely
 */
public record YamlResult(SyntaxNode tree, List<YamlDiagnostic> diagnostics) {

  /**
   * Copies the diagnostics.
   *
   * @throws NullPointerException if the tree or the diagnostics are missing
   */
  public YamlResult {
    Objects.requireNonNull(tree, "tree");
    diagnostics = List.copyOf(diagnostics);
  }

  /** The result of a text with a problem at {@code offset}: an empty document and a diagnostic. */
  static YamlResult failure(final YamlSource source, final String message, final int offset) {
    final int at = Math.min(offset, source.text().length());
    final SyntaxNode empty =
        new SyntaxNode(
            YamlSyntax.DOCUMENT, source.span(source.start(), source.start()), Map.of(), List.of());
    return new YamlResult(
        empty, List.of(new YamlDiagnostic(message, source.line(at), source.column(at))));
  }

  /** Tells whether the text was read without a problem. */
  public boolean valid() {
    return diagnostics.isEmpty();
  }
}
