package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RelatedFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The file that holds a sink and the files it depends on, by the module name an import uses. */
final class NodeProgramFiles {
  private final NodeProgramFile own;
  private final Map<String, RelatedFile> modules = new HashMap<>();
  private final Map<String, NodeProgramFile> opened = new HashMap<>();

  NodeProgramFiles(final SourceUnit unit, final SyntaxNode root, final List<RelatedFile> related) {
    this.own = new NodeProgramFile(unit, root);
    related.forEach(file -> modules.putIfAbsent(NodeModuleNames.of(file.unit().path()), file));
  }

  NodeProgramFile own() {
    return own;
  }

  /** The file that a specifier written in {@code from} names, when it is one of the files. */
  Optional<NodeProgramFile> imported(final NodeProgramFile from, final String specifier) {
    final String module =
        NodeModuleNames.resolve(NodeModuleNames.directoryOf(from.path()), specifier);
    return Optional.ofNullable(modules.get(module))
        .map(file -> opened.computeIfAbsent(module, key -> open(file)));
  }

  private static NodeProgramFile open(final RelatedFile file) {
    return new NodeProgramFile(file.unit(), file.tree());
  }
}
