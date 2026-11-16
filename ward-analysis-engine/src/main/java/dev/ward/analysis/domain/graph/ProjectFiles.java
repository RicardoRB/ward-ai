package dev.ward.analysis.domain.graph;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.Reference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The files of a run that a whole-project rule may report, and the entry files that count as used
 * without an importer.
 *
 * @param files the project-root-relative paths of the analysed files that have a dependency summary
 * @param entries the project-root-relative paths declared as entry points by manifests
 * @param sources the language of every source file of the run, summary or not
 * @param unresolvedImports the references of each file that resolved to no project file, by path
 */
public record ProjectFiles(
    Set<String> files,
    Set<String> entries,
    Map<String,
    AnalysisLanguage> sources,
    Map<String,
    List<Reference>> unresolvedImports
) {
  /** No files and no entries: a whole-project rule reports nothing over it. */
  public static final ProjectFiles EMPTY = new ProjectFiles(Set.of(), Set.of());

  /**
   * Copies the sets.
   *
   * @throws NullPointerException if a field is missing
   */
  public ProjectFiles {
    Objects.requireNonNull(files, "files");
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(unresolvedImports, "unresolvedImports");
    files = Set.copyOf(files);
    entries = Set.copyOf(entries);
    sources = Map.copyOf(sources);
    unresolvedImports =
        unresolvedImports.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> List.copyOf(entry.getValue())
                )
            );
  }

  /** Creates the view of a run that records no unresolved imports. */
  public ProjectFiles(
      final Set<String> files,
      final Set<String> entries,
      final Map<String,
      AnalysisLanguage> sources
  ) {
    this(files, entries, sources, Map.of());
  }

  /** Creates the view of a run whose source files are not known beyond {@code files}. */
  public ProjectFiles(final Set<String> files, final Set<String> entries) {
    this(files, entries, Map.of());
  }
}
