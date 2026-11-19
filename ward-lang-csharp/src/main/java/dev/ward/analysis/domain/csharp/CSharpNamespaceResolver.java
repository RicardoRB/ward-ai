package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Resolves a {@code using} to every other file that declares the namespace it names. A {@code using
 * static} of a type resolves through the longest namespace the name starts with.
 */
public final class CSharpNamespaceResolver implements DependencyResolver {
  private static final String SEPARATOR = ".";

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final Map<String, List<String>> filesByNamespace = new TreeMap<>();
    summaries.forEach(summary -> index(filesByNamespace, summary));
    return summaries.stream()
        .flatMap(summary -> edges(summary, filesByNamespace))
        .toList();
  }

  private static void index(
      final Map<String,
      List<String>> filesByNamespace,
      final FileSummary summary
  ) {
    summary.exports().stream()
        .map(Symbol::name)
        .forEach(
            name -> filesByNamespace.computeIfAbsent(name, key -> new ArrayList<>()).add(
                summary.path()
            )
        );
  }

  private static Stream<DependencyEdge> edges(
      final FileSummary summary,
      final Map<String,
      List<String>> filesByNamespace
  ) {
    return summary.imports().stream()
        .flatMap(
            using ->
                filesDeclaring(using.specifier(), filesByNamespace).stream()
                    .filter(target -> !target.equals(summary.path()))
                    .map(target -> new DependencyEdge(summary.path(), target, using))
        );
  }

  private static List<String> filesDeclaring(
      final String name,
      final Map<String,
      List<String>> filesByNamespace
  ) {
    String candidate = name;
    while (true) {
      final List<String> files = filesByNamespace.get(candidate);
      if (files != null) {
        return files;
      }
      final int dot = candidate.lastIndexOf(SEPARATOR);
      if (dot < 0) {
        return List.of();
      }
      candidate = candidate.substring(0, dot);
    }
  }
}
