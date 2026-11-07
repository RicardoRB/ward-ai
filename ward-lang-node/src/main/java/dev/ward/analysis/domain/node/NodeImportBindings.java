package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** The names the import declarations of a file bind, for the type resolver. */
public final class NodeImportBindings {

  private NodeImportBindings() {}

  /**
   * One imported name.
   *
   * @param local the name in the file
   * @param member the imported name, {@code default}, or {@code *} for the whole module
   * @param specifier the module as written
   */
  public record Import(String local, String member, String specifier) {}

  /** Returns the names bound by the imports under {@code tree}, in source order. */
  public static List<Import> of(final SyntaxNode tree) {
    return new NodeImportScan(tree)
        .bindings().stream()
            .map(
                binding ->
                    new Import(
                        NodeFlowSyntax.text(binding.local()),
                        binding.member(),
                        binding.specifier()))
            .toList();
  }
}
