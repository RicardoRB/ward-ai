package dev.ward.domain.init;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.project.Framework;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The layer boundary of each architecture style, which init writes to {@code ward.toml} for the
 * {@code ward} gate to check.
 */
public final class ArchitectureLayers {
  /** The languages whose dependencies the {@code ward} engine resolves, with type-name edges. */
  public static final Set<AnalysisLanguage> ENGINE_LANGUAGES =
      Set.of(
          AnalysisLanguage.JAVA,
          AnalysisLanguage.PYTHON,
          AnalysisLanguage.TYPESCRIPT,
          AnalysisLanguage.JAVASCRIPT,
          AnalysisLanguage.SWIFT,
          AnalysisLanguage.KOTLIN);

  private static final List<String> FASTAPI_CONTROLLERS = List.of("routers", "api", "endpoints");
  private static final List<String> FASTAPI_REPOSITORIES = List.of("repositories", "crud");

  private ArchitectureLayers() {}

  /**
   * Returns the boundary of {@code style}. An unknown style uses the layered boundary. FastAPI adds
   * its router packages to the source layers and its repository packages to the forbidden ones.
   */
  public static LayerBoundary forStyle(final String style, final Framework framework) {
    final LayerBoundary base = base(style);
    if (framework != Framework.FASTAPI) {
      return base;
    }
    return new LayerBoundary(
        concat(base.from(), FASTAPI_CONTROLLERS), concat(base.forbidden(), FASTAPI_REPOSITORIES));
  }

  private static LayerBoundary base(final String style) {
    return switch (style) {
      case "hexagonal" ->
          new LayerBoundary(List.of("domain", "application"), List.of("adapter", "infrastructure"));
      case "clean" ->
          new LayerBoundary(
              List.of("domain", "core"), List.of("controller", "adapter", "infrastructure"));
      default -> new LayerBoundary(List.of("controller"), List.of("repository"));
    };
  }

  private static List<String> concat(final List<String> first, final List<String> second) {
    return Stream.concat(first.stream(), second.stream()).toList();
  }
}
