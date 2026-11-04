package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import java.util.List;

/** Builds hand-written file summaries and reads the edges a resolver returns for them. */
final class JavaResolverFixtures {
  static final String PACKAGE_A = "package:a";
  static final String PACKAGE_SERVICE = "package:shop.service";
  static final String MAIN = "src/main/java/";
  static final String TEST = "src/test/java/";
  static final List<JavaSourceRoot> ROOTS =
      List.of(
          new JavaSourceRoot("src/main/java", "", false),
          new JavaSourceRoot("src/test/java", "", true));

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  private JavaResolverFixtures() {}

  /** A summary of {@code path} that declares {@code declared} and has the given references. */
  static FileSummary file(
      final String path, final List<String> declared, final List<String> references) {
    final List<Symbol> symbols = declared.stream().map(name -> new Symbol(name, SPAN)).toList();
    return new FileSummary(
        path,
        symbols,
        references.stream().map(specifier -> new Reference(specifier, SPAN)).toList(),
        symbols);
  }

  /** Resolves the summaries with the default source roots. */
  static List<DependencyEdge> resolve(final FileSummary... summaries) {
    return new JavaDependencyResolver(ROOTS).resolve(List.of(summaries));
  }

  /** The sorted targets of the edges leaving {@code from}. */
  static List<String> targets(final List<DependencyEdge> edges, final String from) {
    return edges.stream()
        .filter(edge -> edge.from().equals(from))
        .map(DependencyEdge::to)
        .sorted()
        .toList();
  }
}
