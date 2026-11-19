package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.api.ApiMember;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;

/** Reads Rust source into API types, for tests. */
final class RustApiFixtures {
  private RustApiFixtures() {}

  static List<ApiType> read(final SourceUnit... units) {
    return new RustApiSurfaceReader().read(
        List.of(units),
        new AntlrLanguageFrontend(),
        name -> false
    );
  }

  static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.RUST, text);
  }

  static List<String> names(final List<ApiType> types) {
    return types.stream().map(ApiType::name).toList();
  }

  static ApiType type(final List<ApiType> types, final String name) {
    return types.stream().filter(
        candidate -> candidate.name().equals(name)
    ).findFirst().orElseThrow();
  }

  static List<String> ids(final ApiType type) {
    return type.members().stream().map(ApiMember::id).toList();
  }
}
