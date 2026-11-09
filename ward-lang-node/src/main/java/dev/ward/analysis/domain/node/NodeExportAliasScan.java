package dev.ward.analysis.domain.node;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the names of a file's export lists and default export that bind a local name under an
 * export name: {@code export { a, a as b }} and {@code export default a}. Re-exports from another
 * module are not local bindings and are skipped.
 */
final class NodeExportAliasScan {

  private static final String IDENTIFIER = "Identifier";
  private static final String EXPORT = "export";
  private static final String DEFAULT = "default";
  private static final String AS = "as";
  private static final String FROM = "from";
  private static final String OPEN = "{";
  private static final int ALIASED_ITEM_TOKENS = 3;
  private static final String CLOSE = "}";

  private final NodeTokens tokens;

  NodeExportAliasScan(final NodeTokens tokens) {
    this.tokens = tokens;
  }

  /** The local bindings the file exports, in source order. */
  List<NodeFileFacts.ExportAlias> aliases() {
    final List<NodeFileFacts.ExportAlias> aliases = new ArrayList<>();
    int i = 0;
    while (i < tokens.size()) {
      if (tokens.isKeyword(i, EXPORT) && tokens.isPunctuation(i + 1, OPEN)) {
        i = exportList(i + 1, aliases);
      } else {
        addDefault(i, aliases);
      }
      i++;
    }
    return List.copyOf(aliases);
  }

  private void addDefault(final int i, final List<NodeFileFacts.ExportAlias> aliases) {
    if (tokens.isKeyword(i, EXPORT)
        && tokens.isKeyword(i + 1, DEFAULT)
        && i + 2 < tokens.size()
        && IDENTIFIER.equals(tokens.kind(i + 2))) {
      aliases.add(
          new NodeFileFacts.ExportAlias(tokens.text(i + 2), DEFAULT, tokens.leaf(i + 1).span()));
    }
  }

  /** Reads the items of the list opened at {@code open}; returns the index of its close brace. */
  private int exportList(final int open, final List<NodeFileFacts.ExportAlias> aliases) {
    final List<NodeFileFacts.ExportAlias> items = new ArrayList<>();
    int j = open + 1;
    while (j < tokens.size() && !tokens.isPunctuation(j, CLOSE)) {
      if (IDENTIFIER.equals(tokens.kind(j))) {
        final boolean renamed = tokens.isKeyword(j + 1, AS) && j + 2 < tokens.size();
        final int exportedAt = renamed ? j + 2 : j;
        items.add(
            new NodeFileFacts.ExportAlias(
                tokens.text(j), tokens.text(exportedAt), tokens.leaf(exportedAt).span()));
        j = renamed ? j + ALIASED_ITEM_TOKENS : j + 1;
      } else {
        j++;
      }
    }
    if (j + 1 < tokens.size() && tokens.isKeyword(j + 1, FROM)) {
      return j;
    }
    aliases.addAll(items);
    return j;
  }
}
