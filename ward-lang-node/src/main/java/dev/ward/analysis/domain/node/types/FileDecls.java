package dev.ward.analysis.domain.node.types;

import java.util.List;
import java.util.Map;

/**
 * The declaration index of one file: what it declares, imports and exports.
 *
 * @param isModule whether the file has a top-level import or export, so its names are not global
 * @param imports the imports by local name
 * @param decls the top-level declarations
 * @param exports the export lists and re-exports
 * @param modules the ambient modules declared in the file, by specifier
 * @param references the files and libraries named by reference directives, as written
 */
public record FileDecls(
    boolean isModule,
    Map<String, Import> imports,
    List<Decl> decls,
    List<Export> exports,
    Map<String, List<Decl>> modules,
    List<String> references) {

  /** An empty index. */
  public static final FileDecls EMPTY =
      new FileDecls(false, Map.of(), List.of(), List.of(), Map.of(), List.of());

  /**
   * An import.
   *
   * @param specifier the module as written
   * @param member the imported name, {@code default}, or {@code *} for the whole module
   */
  public record Import(String specifier, String member) {}

  /**
   * One export clause.
   *
   * @param exported the exported name; {@code *} for a star re-export and {@code =} for an export
   *     assignment
   * @param local the local name, or the name taken from {@code specifier}
   * @param specifier the module re-exported from, or empty for a local export
   */
  public record Export(String exported, String local, String specifier) {}

  /** Copies the collections. */
  public FileDecls {
    imports = Map.copyOf(imports);
    decls = List.copyOf(decls);
    exports = List.copyOf(exports);
    modules = Map.copyOf(modules);
    references = List.copyOf(references);
  }
}
