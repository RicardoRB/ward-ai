package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiSurfaceReader;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Reads the public API of Java source files with the source model of the type facts: the types and
 * members that code outside the package can use.
 */
public final class JavaApiSurfaceReader implements ApiSurfaceReader {

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.JAVA;
  }

  @Override
  public List<ApiType> read(
      final List<SourceUnit> units,
      final LanguageFrontend frontend,
      final Predicate<String> libraryExists) {
    final List<ParsedUnit> parsed = units.stream().map(unit -> parse(unit, frontend)).toList();
    final Set<String> project = new HashSet<>();
    parsed.forEach(unit -> project.addAll(unit.names()));
    final List<ApiType> found = new ArrayList<>();
    for (final ParsedUnit unit : parsed) {
      final Set<String> imported = imported(unit);
      final JavaUnitModel model =
          JavaUnitModel.of(
              unit.result().tree(),
              name ->
                  project.contains(name) || imported.contains(name) || libraryExists.test(name));
      final JavaApiTypeReader reader = new JavaApiTypeReader(unit.path());
      model
          .classes()
          .forEach(declaration -> reader.read(declaration, null, false, new ApiTypeRefs(), found));
    }
    found.sort(Comparator.comparing(ApiType::name));
    return List.copyOf(found);
  }

  /** The single-type imports of a file: a name that is imported is taken to exist. */
  private static Set<String> imported(final ParsedUnit unit) {
    return Imports.of(unit.result().tree()).single().values().stream().collect(Collectors.toSet());
  }

  private static ParsedUnit parse(final SourceUnit unit, final LanguageFrontend frontend) {
    final ParseResult result = frontend.parse(unit);
    if (!result.diagnostics().isEmpty()) {
      final AnalysisFinding first = result.diagnostics().getFirst();
      throw new ApiSurfaceException(
          unit.path(), "syntax error" + (first.line() == null ? "" : " at line " + first.line()));
    }
    return new ParsedUnit(unit.path(), result, topLevelNames(result));
  }

  private static List<String> topLevelNames(final ParseResult result) {
    final Imports imports = Imports.of(result.tree());
    final List<String> names = new ArrayList<>();
    result.tree().children().stream()
        .filter(child -> "typeDeclaration".equals(child.kind()))
        .forEach(
            child ->
                JavaClassReader.topLevel(child)
                    .ifPresent(
                        entry ->
                            names.addAll(
                                nested(entry, BinaryNames.qualified(imports.packageName(), "")))));
    return names;
  }

  private static List<String> nested(final JavaClassReader.Entry entry, final String prefix) {
    final String binary = prefix + entry.name();
    final List<String> names = new ArrayList<>(List.of(binary));
    JavaClassReader.bodyEntries(entry.declaration()).stream()
        .filter(inner -> JavaClassReader.isType(inner.declaration()))
        .forEach(inner -> names.addAll(nested(inner, binary + '$')));
    return names;
  }

  /** A parsed file with the binary names of the classes it declares. */
  private record ParsedUnit(String path, ParseResult result, List<String> names) {}
}
