package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.node.NodeManifest.Member;
import dev.ward.analysis.domain.node.NodeManifest.Value;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports an optional peer dependency that a file of its package imports, as knip reports a
 * referenced optional peer dependency. The finding sits at the peer's key in {@code
 * peerDependencies}.
 */
public final class NodeOptionalPeerCheck implements RuleCheck, ProjectCheck {

  private static final String VALUE = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the peer dependency name
   */
  public NodeOptionalPeerCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
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
    final Map<String, NodeManifests.Manifest> manifests = NodeManifests.byDirectory(files);
    final Map<String, Set<String>> imports = NodeManifests.importsByDirectory(trees, manifests);
    final List<ProjectMatch> found = new ArrayList<>();
    for (final NodeManifests.Manifest owner : manifests.values()) {
      final Set<String> used = imports.getOrDefault(owner.directory(), Set.of());
      final Set<String> optional = optionalPeers(owner.manifest());
      for (final Member peer : owner.manifest().entries("peerDependencies")) {
        if (optional.contains(peer.key()) && used.contains(peer.key())) {
          found.add(new ProjectMatch(owner.path(), match(peer)));
        }
      }
    }
    return List.copyOf(found);
  }

  /** The peers that {@code peerDependenciesMeta} marks {@code "optional": true}. */
  private static Set<String> optionalPeers(final NodeManifest manifest) {
    final Set<String> optional = new HashSet<>();
    for (final Member meta : manifest.entries("peerDependenciesMeta")) {
      if (meta.value() instanceof Value.Obj settings
          && settings.members().stream()
              .anyMatch(
                  setting ->
                      "optional".equals(setting.key())
                          && setting.value() instanceof Value.Bool flag
                          && flag.value())) {
        optional.add(meta.key());
      }
    }
    return optional;
  }

  private RuleMatch match(final Member peer) {
    final Span at = new Span(peer.line(), peer.column(), peer.line(), peer.column(), 0, 0);
    return new RuleMatch(at, message.replace(VALUE, peer.key()));
  }
}
