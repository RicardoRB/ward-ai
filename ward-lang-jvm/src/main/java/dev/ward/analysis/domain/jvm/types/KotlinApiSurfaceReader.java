package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiSurfaceReader;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads the public API of Kotlin source files: the types, with Kotlin's visibility, modality and
 * nullability, and the top-level functions and properties of each package as one type named {@code
 * <package>.<top-level>}, because callers in Kotlin see a package, not the file that declares them.
 */
public final class KotlinApiSurfaceReader implements ApiSurfaceReader {

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.KOTLIN;
  }

  @Override
  public List<ApiType> read(
      final List<SourceUnit> units,
      final LanguageFrontend frontend,
      final Predicate<String> libraryExists) {
    final List<Parsed> parsed = units.stream().map(unit -> parse(unit, frontend)).toList();
    final Set<String> project = new HashSet<>();
    parsed.forEach(unit -> project.addAll(unit.names()));
    final Predicate<String> exists = name -> project.contains(name) || libraryExists.test(name);
    final List<ApiType> found = new ArrayList<>();
    final KotlinApiTopLevel topLevel = new KotlinApiTopLevel();
    parsed.stream()
        .map(unit -> new KotlinApiUnitReader(unit.path(), unit.tree(), exists))
        .forEach(reader -> reader.read(found, topLevel));
    found.addAll(topLevel.types());
    found.sort(Comparator.comparing(ApiType::name));
    return List.copyOf(found);
  }

  private static Parsed parse(final SourceUnit unit, final LanguageFrontend frontend) {
    final ParseResult result = frontend.parse(unit);
    if (!result.diagnostics().isEmpty()) {
      final AnalysisFinding first = result.diagnostics().getFirst();
      throw new ApiSurfaceException(
          unit.path(), "syntax error" + (first.line() == null ? "" : " at line " + first.line()));
    }
    final String prefix = KotlinTypes.imports(result.tree()).packageName();
    final Set<String> names = new HashSet<>();
    KotlinApiUnitReader.topLevelObjects(result.tree()).stream()
        .filter(KotlinApiTypeReader::isType)
        .forEach(
            node ->
                collect(
                    node, BinaryNames.qualified(prefix, KotlinApiTypeReader.nameOf(node)), names));
    return new Parsed(unit.path(), result.tree(), names);
  }

  private static void collect(final SyntaxNode node, final String binary, final Set<String> names) {
    names.add(binary);
    KotlinApiTypeReader.declarations(node).stream()
        .filter(KotlinApiTypeReader::isType)
        .forEach(
            nested -> collect(nested, binary + '$' + KotlinApiTypeReader.nameOf(nested), names));
  }

  /** A parsed file with the binary names of the types it declares. */
  private record Parsed(String path, SyntaxNode tree, Set<String> names) {}
}
