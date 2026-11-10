package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeFileFacts.ModuleSpecifier;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports a relative import, re-export or require that names no file of the project, as knip
 * reports an unresolved import. A file counts when it exists, whatever its language, so an import
 * of a {@code .vue} or stylesheet resolves. A specifier that names a hidden or build folder is
 * unknown and never a finding, and a bare specifier is never one: it may name an installed package
 * or a workspace, which needs manifests this check does not read.
 *
 * <p>shortcut: resolves relative specifiers against the project's files and the module resolver's
 * extension and index rules, without tsconfig paths or package exports; upgrade by passing the
 * project layout to the check.
 */
public final class NodeUnresolvedImportCheck implements RuleCheck, ProjectCheck {

  private static final String RELATIVE = ".";
  private static final String DOT = ".";
  private static final Set<String> CURRENT_OR_PARENT = Set.of(".", "..");
  private static final String SEPARATOR = "/";
  private static final Set<String> UNKNOWN_FOLDERS =
      Set.of("node_modules", "target", "build", "dist", "mutants");
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
    return project(trees, ProjectFiles.empty());
  }

  @Override
  public List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    final Set<String> known = new HashSet<>(trees.keySet());
    known.addAll(files.existingPaths());
    final NodeModuleResolver resolver =
        new NodeModuleResolver(new NodeProjectLayout(known, List.of(), List.of()));
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach(
        (path, tree) -> {
          for (final ModuleSpecifier module : NodeFileFacts.specifiers(tree)) {
            if (isChecked(module.specifier())
                && resolver.resolve(path, module.specifier()).isEmpty()) {
              found.add(new ProjectMatch(path, match(module)));
            }
          }
        });
    return List.copyOf(found);
  }

  /** A relative specifier whose every folder is known: not hidden, and not build output. */
  private static boolean isChecked(final String specifier) {
    return specifier.startsWith(RELATIVE)
        && Arrays.stream(specifier.split(SEPARATOR))
            .noneMatch(NodeUnresolvedImportCheck::isUnknown);
  }

  private static boolean isUnknown(final String segment) {
    return UNKNOWN_FOLDERS.contains(segment)
        || (segment.startsWith(DOT) && !CURRENT_OR_PARENT.contains(segment));
  }

  private RuleMatch match(final ModuleSpecifier module) {
    return new RuleMatch(module.span(), message.replace(VALUE, module.specifier()));
  }
}
