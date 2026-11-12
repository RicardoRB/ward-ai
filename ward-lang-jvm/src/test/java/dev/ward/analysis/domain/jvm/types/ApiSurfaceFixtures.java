package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads Java sources into API types for the reader tests, with the test JVM's classes. */
final class ApiSurfaceFixtures {
  static final String PACKAGE = "package p;\n";

  private ApiSurfaceFixtures() {}

  static List<ApiType> read(final String... sources) {
    final List<SourceUnit> units = new ArrayList<>();
    for (int index = 0; index < sources.length; index++) {
      units.add(new SourceUnit("p/F" + index + ".java", AnalysisLanguage.JAVA, sources[index]));
    }
    return new JavaApiSurfaceReader()
        .read(
            units,
            new AntlrLanguageFrontend(),
            name -> ClassLoader.getSystemResource(name.replace('.', '/') + ".class") != null);
  }

  static Optional<ApiType> type(final List<ApiType> types, final String name) {
    return types.stream().filter(found -> found.name().equals(name)).findFirst();
  }

  static List<String> ids(final ApiType type) {
    return type.members().stream().filter(type::exposes).map(ApiMember::id).toList();
  }

  static ApiMember member(final ApiType type, final String id) {
    return type.members().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
  }
}
