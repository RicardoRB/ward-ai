package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeFileFacts.ImportedName;
import dev.ward.analysis.domain.node.NodeFileFacts.MemberUsage;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * How the whole project uses one enum or namespace, through the imports that resolve to its file.
 */
final class NodeMemberUses {

  private static final String NAMESPACE_IMPORT = "*";

  private NodeMemberUses() {}

  /**
   * The combined use of {@code name}, declared in {@code path}, by the file and by its importers.
   */
  static MemberUsage of(
      final String path,
      final String name,
      final SortedMap<String, SyntaxNode> trees,
      final NodeModuleResolver resolver) {
    final List<MemberUsage> usages = new ArrayList<>();
    usages.add(NodeFileFacts.memberUsage(trees.get(path), name));
    for (final Map.Entry<String, SyntaxNode> entry : trees.entrySet()) {
      if (!entry.getKey().equals(path)) {
        usages.addAll(reaching(entry.getValue(), entry.getKey(), path, name, resolver));
      }
    }
    return combine(usages);
  }

  /**
   * The uses that the imports of {@code tree}, a file at {@code from}, make of {@code name} in
   * {@code path}.
   */
  private static List<MemberUsage> reaching(
      final SyntaxNode tree,
      final String from,
      final String path,
      final String name,
      final NodeModuleResolver resolver) {
    final List<MemberUsage> usages = new ArrayList<>();
    for (final ImportedName imported : NodeFileFacts.importedNames(tree)) {
      if (declares(resolver, from, imported, path)) {
        reachedBy(tree, imported, name).ifPresent(usages::add);
      }
    }
    return usages;
  }

  /**
   * The use an import makes of the declaring module's {@code name}, or empty when it names another.
   */
  private static Optional<MemberUsage> reachedBy(
      final SyntaxNode tree, final ImportedName imported, final String name) {
    if (NAMESPACE_IMPORT.equals(imported.member())) {
      return Optional.of(new MemberUsage(Set.of(), true));
    }
    if (name.equals(imported.member())) {
      return Optional.of(NodeFileFacts.memberUsage(tree, imported.local()));
    }
    return Optional.empty();
  }

  private static boolean declares(
      final NodeModuleResolver resolver,
      final String from,
      final ImportedName imported,
      final String path) {
    return resolver.resolve(from, imported.specifier()).filter(path::equals).isPresent();
  }

  private static MemberUsage combine(final List<MemberUsage> usages) {
    final Set<String> members = new HashSet<>();
    boolean escaped = false;
    for (final MemberUsage usage : usages) {
      members.addAll(usage.members());
      escaped = escaped || usage.escaped();
    }
    return new MemberUsage(Set.copyOf(members), escaped);
  }
}
