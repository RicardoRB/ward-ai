package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeModuleScan;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.node.NodeReference;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Reports a relative import, re-export or require that names no file of the project, as knip
 * reports an unresolved import. A bare specifier is never a finding: it may name an installed
 * package or a workspace, which needs manifests this check does not read.
 *
 * <p>shortcut: resolves relative specifiers against the project's files and the module resolver's
 * extension and index rules, without tsconfig paths or package exports; upgrade by passing the
 * project layout to the check.
 */
public final class NodeUnresolvedImportCheck implements RuleCheck, ProjectCheck {

  private static final String RELATIVE = ".";
  private static final String VALUE = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the specifier
   */
  public NodeUnresolvedImportCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    final NodeModuleResolver resolver =
        new NodeModuleResolver(new NodeProjectLayout(trees.keySet(), List.of(), List.of()));
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach(
        (path, tree) -> {
          for (final Reference reference : references(tree)) {
            final Optional<String> specifier = specifier(reference);
            if (specifier.isPresent()
                && specifier.get().startsWith(RELATIVE)
                && resolver.resolve(path, specifier.get()).isEmpty()) {
              found.add(new ProjectMatch(path, match(reference, specifier.get())));
            }
          }
        });
    return List.copyOf(found);
  }

  private static List<Reference> references(final SyntaxNode tree) {
    final NodeModuleScan scan = new NodeModuleScan(tree);
    scan.run();
    return scan.references();
  }

  private static Optional<String> specifier(final Reference reference) {
    return NodeReference.parse(reference.specifier()).map(NodeReference::specifier);
  }

  private RuleMatch match(final Reference reference, final String specifier) {
    return new RuleMatch(reference.span(), message.replace(VALUE, specifier));
  }
}
