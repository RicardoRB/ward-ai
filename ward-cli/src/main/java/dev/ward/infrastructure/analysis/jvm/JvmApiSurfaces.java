package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.api.ApiSurfaceReader;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.application.gate.apicompat.ApiSurfaces;
import dev.ward.infrastructure.rule.BundledLanguages;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Reads the API of JVM source files with the bundled readers and the JDK image. */
public final class JvmApiSurfaces implements ApiSurfaces {

  /** Creates the adapter. */
  @Inject
  public JvmApiSurfaces() {}

  @Override
  public List<ApiType> read(final Path root, final List<SourceUnit> units) {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final List<ApiType> found = new ArrayList<>();
    try (JvmClassLibraries libraries =
        JvmClassLibraries.open(
            root,
            List.of(),
            JvmClassLibraries.jdkHome(
                Optional.ofNullable(System.getenv("JAVA_HOME")),
                Optional.ofNullable(System.getProperty("java.home"))),
            Optional.empty())) {
      for (final ApiSurfaceReader reader : BundledLanguages.apiSurfaceReaders()) {
        final List<SourceUnit> own = ofLanguage(units, reader.language());
        if (!own.isEmpty()) {
          found.addAll(reader.read(own, frontend, libraries::exists));
        }
      }
    }
    return List.copyOf(found);
  }

  private static List<SourceUnit> ofLanguage(
      final List<SourceUnit> units, final AnalysisLanguage language) {
    return units.stream().filter(unit -> unit.language() == language).collect(Collectors.toList());
  }
}
