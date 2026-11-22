package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiSurfaceReader;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Reads the public API of Rust library crates. Module paths follow the files ({@code src/a/mod.rs}
 * and {@code src/a.rs} are module {@code a}); a type is named {@code crate.a.Type}, and the free
 * functions and constants of a module form one type {@code crate.a.<top-level>}.
 *
 * <p>shortcut: {@code #[path]} modules, glob re-exports, macro-generated items, {@code cfg} and
 * {@code where} clauses are not read; read them if a crate that uses them shows up.
 */
public final class RustApiSurfaceReader implements ApiSurfaceReader {
  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.RUST;
  }

  @Override
  public List<ApiType> read(
      final List<SourceUnit> units,
      final LanguageFrontend frontend,
      final Predicate<String> libraryExists
  ) {
    final List<SourceUnit> library =
        units.stream().filter(unit -> !RustApiPaths.skipped(unit.path())).toList();
    final RustApiDecls.Builder decls = new RustApiDecls.Builder();
    library.stream().map(unit -> declarations(unit, frontend)).forEach(decls::addAll);
    final Set<String> roots =
        library.stream()
            .filter(unit -> RustApiPaths.isRoot(unit.path()))
            .map(unit -> RustApiPaths.module(unit.path()))
            .collect(Collectors.toSet());
    return new RustApiCrate(decls.build(), roots).types();
  }

  private static RustApiDecls declarations(final SourceUnit unit, final LanguageFrontend frontend) {
    final ParseResult result = frontend.parse(unit);
    if (!result.diagnostics().isEmpty()) {
      final AnalysisFinding first = result.diagnostics().getFirst();
      throw new ApiSurfaceException(
          unit.path(),
          "syntax error" + (first.line() == null ? "" : " at line " + first.line())
      );
    }
    final SyntaxNode tree = result.tree();
    return new RustApiFile(unit).read(tree, RustApiPaths.module(unit.path()));
  }
}
