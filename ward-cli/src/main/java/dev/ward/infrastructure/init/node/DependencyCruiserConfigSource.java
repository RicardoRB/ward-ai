package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * Renders Ward's dependency-cruiser config for an architecture style. Its forbidden dependency is
 * the one the generated ArchUnit test forbids for the same style, so both gates agree. The text is
 * laid out the way Prettier formats it at its default width, so the project's {@code prettier} gate
 * passes on the generated file.
 */
final class DependencyCruiserConfigSource {
  /** The ownership marker, which tells a rewrite that the config is Ward's own. */
  static final String MARKER = "// managed-by: ward\n";

  private static final String EXCLUDE =
      "(^|/)(node_modules|dist|build|coverage|reports|\\\\.ward|\\\\.stryker-tmp)/";
  private static final String NO_CIRCULAR =
      """
          {
            name: "no-circular",
            severity: "error",
            from: {},
            to: { circular: true },
          },
      """;
  private static final String TYPESCRIPT_OPTIONS = "    tsPreCompilationDeps: true,\n";
  private static final String TSCONFIG_OPTION = "    tsConfig: { fileName: \"tsconfig.json\" },\n";
  private static final String TEMPLATE =
      """
      export default {
        forbidden: [
          // @COMMENT@
          {
            name: "@NAME@",
            severity: "error",
            from: { path: "@FROM@" },
            to: { path: "@TO@" },
          },
      @NO_CIRCULAR@  ],
        options: {
          doNotFollow: { path: "node_modules" },
          exclude: {
            path: "@EXCLUDE@",
          },
      @OPTIONS@  },
      };
      """;

  private DependencyCruiserConfigSource() {}

  /**
   * Returns the marked config for {@code architecture}. {@code extras} adds {@code no-circular}
   * when it sets cycles, which only {@code strict} does. A TypeScript project counts type-only
   * imports, and names {@code tsconfig.json} only when {@code tsConfig} is true.
   */
  static String render(
      final String architecture,
      final QualityPreset.ArchitectureExtras extras,
      final boolean typescript,
      final boolean tsConfig) {
    final Boundary boundary = Boundary.of(architecture);
    return MARKER
        + TEMPLATE
            .replace("@COMMENT@", boundary.comment())
            .replace("@NAME@", boundary.name())
            .replace("@FROM@", boundary.fromPath())
            .replace("@TO@", boundary.toPath())
            .replace("@NO_CIRCULAR@", extras.cycles() ? NO_CIRCULAR : "")
            .replace("@EXCLUDE@", EXCLUDE)
            .replace("@OPTIONS@", typescriptOptions(typescript, tsConfig));
  }

  private static String typescriptOptions(final boolean typescript, final boolean tsConfig) {
    if (!typescript) {
      return "";
    }
    return tsConfig ? TYPESCRIPT_OPTIONS + TSCONFIG_OPTION : TYPESCRIPT_OPTIONS;
  }

  /**
   * The style's forbidden dependency: modules in a {@code from} layer must not import a {@code to}
   * layer. A layer is a directory with that name anywhere in the module's path.
   */
  private record Boundary(String name, String comment, List<String> from, List<String> to) {
    static Boundary of(final String architecture) {
      return switch (architecture) {
        case "hexagonal" ->
            new Boundary(
                "hexagonal-architecture",
                "domain and application must not depend on adapters or infrastructure.",
                List.of("domain", "application"),
                List.of("adapter", "infrastructure"));
        case "clean" ->
            new Boundary(
                "clean-architecture",
                "domain and core must not depend on controllers, adapters or infrastructure.",
                List.of("domain", "core"),
                List.of("controller", "adapter", "infrastructure"));
        default ->
            new Boundary(
                "layered-architecture",
                "controllers must not depend on repositories.",
                List.of("controller"),
                List.of("repository"));
      };
    }

    String fromPath() {
      return path(from);
    }

    String toPath() {
      return path(to);
    }

    private static String path(final List<String> layers) {
      return "(^|/)(" + String.join("|", layers) + ")/";
    }
  }
}
