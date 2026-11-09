package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeCatalogs;
import dev.ward.analysis.domain.node.NodeCatalogs.CatalogEntry;
import dev.ward.analysis.domain.node.NodeManifest.Member;
import dev.ward.analysis.domain.node.NodeManifest.Value;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * Checks the pnpm catalogs of {@code pnpm-workspace.yaml} against the {@code catalog:} references
 * of the package manifests. In the unused mode it reports a catalog entry that no manifest
 * references; in the references mode it reports a {@code catalog:} reference to an entry the
 * catalogs do not define, which pnpm would reject. {@code catalog:} with no name refers to the
 * default catalog.
 */
public final class NodeCatalogCheck implements RuleCheck, ProjectCheck {

  private static final String WORKSPACE = "pnpm-workspace.yaml";
  private static final String CATALOG = "catalog:";
  private static final List<String> SECTIONS =
      List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
  private static final String VALUE = "{value}";

  private final String message;
  private final boolean references;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the dependency name
   * @param references true to report undefined references, false to report unused entries
   */
  public NodeCatalogCheck(final String message, final boolean references) {
    this.message = Objects.requireNonNull(message, "message");
    this.references = references;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    final Optional<String> workspace = files.text(WORKSPACE);
    if (workspace.isEmpty()) {
      return List.of();
    }
    final List<CatalogEntry> catalogs = NodeCatalogs.parse(workspace.get());
    final Set<String> referenced = new HashSet<>();
    final List<ProjectMatch> found = new ArrayList<>();
    NodeManifests.byDirectory(files)
        .values()
        .forEach(owner -> found.addAll(references(owner, catalogs, referenced)));
    if (!references) {
      found.addAll(unusedEntries(catalogs, referenced));
    }
    return List.copyOf(found);
  }

  /** The catalog references of one manifest; each is recorded as referenced. */
  private List<ProjectMatch> references(
      final NodeManifests.Manifest owner,
      final List<CatalogEntry> catalogs,
      final Set<String> referenced) {
    final List<ProjectMatch> found = new ArrayList<>();
    for (final String section : SECTIONS) {
      for (final Member dependency : owner.manifest().entries(section)) {
        if (dependency.value() instanceof Value.Text text && text.value().startsWith(CATALOG)) {
          final String catalog = catalogOf(text.value());
          referenced.add(catalog + "/" + dependency.key());
          if (references && !defines(catalogs, catalog, dependency.key())) {
            found.add(
                new ProjectMatch(
                    owner.path(), match(dependency.line(), dependency.column(), dependency.key())));
          }
        }
      }
    }
    return found;
  }

  /** The catalog entries that no manifest references. */
  private List<ProjectMatch> unusedEntries(
      final List<CatalogEntry> catalogs, final Set<String> referenced) {
    final List<ProjectMatch> found = new ArrayList<>();
    for (final CatalogEntry entry : catalogs) {
      if (!referenced.contains(entry.catalog() + "/" + entry.dependency())) {
        found.add(
            new ProjectMatch(WORKSPACE, match(entry.line(), entry.column(), entry.dependency())));
      }
    }
    return found;
  }

  private static String catalogOf(final String value) {
    final String name = value.substring(CATALOG.length());
    return name.isEmpty() ? NodeCatalogs.DEFAULT : name;
  }

  private static boolean defines(
      final List<CatalogEntry> catalogs, final String catalog, final String dependency) {
    return catalogs.stream()
        .anyMatch(
            entry -> entry.catalog().equals(catalog) && entry.dependency().equals(dependency));
  }

  private RuleMatch match(final int line, final int column, final String dependency) {
    final Span at = new Span(line, column, line, column, 0, 0);
    return new RuleMatch(at, message.replace(VALUE, dependency));
  }
}
