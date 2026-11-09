package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The facts of one TypeScript or JavaScript file that the project-wide dead-code checks read: its
 * module references, its exported names and what each export is declared as, and the names it
 * imports. The scans stay package-private; checks in other packages use this facade.
 */
public final class NodeFileFacts {

  private NodeFileFacts() {}

  /**
   * A module specifier written in a file.
   *
   * @param specifier the specifier as written, without quotes
   * @param span where the specifier is written
   */
  public record ModuleSpecifier(String specifier, Span span) {}

  /** Returns the module specifiers of the file, in source order, of every kind. */
  public static List<ModuleSpecifier> specifiers(final SyntaxNode tree) {
    final NodeModuleScan scan = new NodeModuleScan(tree);
    scan.run();
    return scan.references().stream()
        .map(
            reference ->
                NodeReference.parse(reference.specifier())
                    .map(parsed -> new ModuleSpecifier(parsed.specifier(), reference.span())))
        .flatMap(Optional::stream)
        .toList();
  }

  /** Returns the exported names of the file, in source order. */
  public static List<Symbol> exports(final SyntaxNode tree) {
    final NodeModuleScan scan = new NodeModuleScan(tree);
    scan.run();
    return scan.exports();
  }

  /** Returns the keyword that declares each exported declaration of the file, by name. */
  public static Map<String, String> exportKinds(final SyntaxNode tree) {
    final NodeModuleScan scan = new NodeModuleScan(tree);
    scan.run();
    return scan.exportKinds();
  }

  /**
   * Returns the member names the file imports: the exported name, {@code default} for a default
   * import, or {@code *} for a namespace import.
   */
  public static List<String> importedMembers(final SyntaxNode tree) {
    return new NodeImportScan(tree)
        .bindings().stream().map(NodeImportScan.Binding::member).toList();
  }

  /**
   * Returns the package a bare specifier names, such as {@code @scope/pkg} for {@code
   * @scope/pkg/sub}, or empty for a relative, absolute, subpath, Node built-in import.
   */
  public static String packageName(final String specifier) {
    if (specifier.startsWith(".")
        || specifier.startsWith("/")
        || specifier.startsWith("#")
        || specifier.startsWith("node:")
        || NodeBuiltins.NAMES.contains(specifier.split("/", 2)[0])) {
      return "";
    }
    final String[] parts = specifier.split("/");
    if (specifier.startsWith("@")) {
      return parts.length >= 2 ? parts[0] + "/" + parts[1] : "";
    }
    return parts[0];
  }
}
