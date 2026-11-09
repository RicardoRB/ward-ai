package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
   * A local binding exported under a name.
   *
   * @param local the local name
   * @param exported the export name, {@code default} for a default export
   * @param span where the export name is written
   */
  public record ExportAlias(String local, String exported, Span span) {}

  /**
   * How a file uses a name bound to an enum or namespace.
   *
   * @param members the members reached as {@code name.member}
   * @param escaped whether the name is used in any other way, which uses every member
   */
  public record MemberUsage(Set<String> members, boolean escaped) {

    /** Copies the members in and out. */
    public MemberUsage {
      members = Set.copyOf(members);
    }

    @Override
    public Set<String> members() {
      return Set.copyOf(members);
    }
  }

  /**
   * An imported binding: the member of the module it names, the local name, and the specifier.
   *
   * @param member the exported name, {@code default}, or {@code *} for a namespace import
   * @param local the local name the file binds
   * @param specifier the module specifier, without quotes
   */
  public record ImportedName(String member, String local, String specifier) {}

  /** Returns the local bindings the file exports under a name, in source order. */
  public static List<ExportAlias> exportAliases(final SyntaxNode tree) {
    return new NodeExportAliasScan(new NodeTokens(NodeLeaves.of(tree))).aliases();
  }

  /** Returns the members the file declares for the enum or namespace {@code name}. */
  public static List<Symbol> declaredMembers(
      final SyntaxNode tree, final String name, final String keyword) {
    return new NodeMemberScan(new NodeTokens(NodeLeaves.of(tree))).declaredMembers(name, keyword);
  }

  /** Returns how the file uses {@code local}, a name bound to an enum or namespace. */
  public static MemberUsage memberUsage(final SyntaxNode tree, final String local) {
    return new NodeMemberScan(new NodeTokens(NodeLeaves.of(tree))).usage(local);
  }

  /** Returns the bindings the file imports, with the member each names and its module specifier. */
  public static List<ImportedName> importedNames(final SyntaxNode tree) {
    return new NodeImportScan(tree)
        .bindings().stream()
            .map(
                binding ->
                    new ImportedName(
                        binding.member(), NodeLeaves.text(binding.local()), binding.specifier()))
            .toList();
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
