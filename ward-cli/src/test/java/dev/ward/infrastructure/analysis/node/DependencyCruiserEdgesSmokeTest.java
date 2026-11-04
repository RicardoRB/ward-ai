package dev.ward.infrastructure.analysis.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regenerates each fixture's dependency-cruiser edges with a real dependency-cruiser and checks
 * them against the committed golden list. Needs a local install, for example {@code ./mvnw
 * -Dtest=DependencyCruiserEdgesSmokeTest -Dward.depcruise.binary=node_modules/.bin/depcruise test}
 * after {@code npm install dependency-cruiser@18.6.0}.
 *
 * <p>The run uses Ward's generated options plus {@code enhancedResolveOptions}, because
 * dependency-cruiser's defaults do not follow package {@code exports} or tsconfig {@code paths}.
 * Workspace packages are linked into {@code node_modules} the way package managers do.
 */
@EnabledIfSystemProperty(named = "ward.depcruise.binary", matches = ".+")
class DependencyCruiserEdgesSmokeTest {
  private static final String GOLDEN = "depcruise-edges.json";
  private static final String TSCONFIG = "tsconfig.json";
  private static final String NODE_MODULES = "node_modules";
  private static final String NODE_MODULES_PREFIX = "node_modules/";
  private static final long TIMEOUT_SECONDS = 120;
  private static final String EXCLUDE =
      "(^|/)(node_modules|dist|build|coverage|reports|\\\\.ward|\\\\.stryker-tmp)/";
  private static final String TYPESCRIPT_OPTIONS = "    tsPreCompilationDeps: true,\n";
  private static final String TSCONFIG_OPTION = "    tsConfig: { fileName: \"tsconfig.json\" },\n";
  private static final String RESOLVE_OPTIONS =
      "    enhancedResolveOptions: { exportsFields: [\"exports\"], conditionNames: [\"types\", \"import\", \"default\"] },\n";

  @TempDir Path work;

  @Test
  void committedEdgesEqualARealDependencyCruiser()
      throws IOException, URISyntaxException, InterruptedException {
    final String binary = System.getProperty("ward.depcruise.binary");
    final List<Fixture> fixtures =
        List.of(
            new Fixture("node-resolution", true, List.of(new Link("lib", "packages/lib"))),
            new Fixture("turborepo-workspace", true, List.of(new Link("@repo/ui", "packages/ui"))),
            new Fixture("node-architecture/typescript", true, List.of()),
            new Fixture("node-architecture/javascript", false, List.of()));
    for (final Fixture fixture : fixtures) {
      final Path copy = copy(fixture);
      link(copy, fixture.links());
      final Path config = write(copy, fixture.typescript());
      final List<String> actual = depcruise(binary, copy, config);
      assertThat(actual)
          .as(fixture.name())
          .isEqualTo(
              NodeResolutionReferenceTest.goldenEdges(
                  NodeDependencyEdgesTest.fixture(fixture.name()).resolve(GOLDEN)));
    }
  }

  private Path copy(final Fixture fixture) throws IOException, URISyntaxException {
    final Path source = NodeDependencyEdgesTest.fixture(fixture.name());
    final Path target = work.resolve(fixture.name().replace('/', '_'));
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path to = target.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(to);
        } else {
          Files.createDirectories(to.getParent());
          Files.copy(file, to);
        }
      }
    }
    return target;
  }

  private static void link(final Path copy, final List<Link> links) throws IOException {
    for (final Link link : links) {
      final Path target = copy.resolve(NODE_MODULES).resolve(link.name());
      Files.createDirectories(target.getParent());
      Files.createSymbolicLink(target, copy.resolve(link.directory()));
    }
  }

  private static Path write(final Path copy, final boolean typescript) throws IOException {
    final String tsOptions =
        typescript
            ? TYPESCRIPT_OPTIONS + (Files.exists(copy.resolve(TSCONFIG)) ? TSCONFIG_OPTION : "")
            : "";
    final String body =
        "module.exports = {\n  options: {\n"
            + "    doNotFollow: { path: \"node_modules\" },\n"
            + "    exclude: { path: \""
            + EXCLUDE
            + "\" },\n"
            + tsOptions
            + RESOLVE_OPTIONS
            + "  },\n};\n";
    final Path config = copy.getParent().resolve(copy.getFileName() + ".depcruise.cjs");
    Files.writeString(config, body);
    return config;
  }

  private static List<String> depcruise(final String binary, final Path copy, final Path config)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(binary, "--output-type", "json", "--config", config.toString(), ".")
            .directory(copy.toFile())
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final String errors =
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).as(errors).isZero();
    return edges(copy, output);
  }

  /** The sorted project-file edges of a dependency-cruiser JSON report. */
  private static List<String> edges(final Path copy, final String report) throws IOException {
    final Map<?, ?> json = (Map<?, ?>) new ForyJsonCodec().fromJson(report, Object.class);
    final List<?> modules = (List<?>) json.get("modules");
    final List<String> sources = new ArrayList<>();
    for (final Object module : modules) {
      final Map<?, ?> map = (Map<?, ?>) module;
      if (!Boolean.TRUE.equals(map.get("coreModule"))) {
        sources.add((String) map.get("source"));
      }
    }
    final List<String> edges = new ArrayList<>();
    for (final Object module : modules) {
      final Map<?, ?> map = (Map<?, ?>) module;
      for (final Object dependency : (List<?>) map.get("dependencies")) {
        final Map<?, ?> edge = (Map<?, ?>) dependency;
        if (Boolean.TRUE.equals(edge.get("coreModule"))
            || Boolean.TRUE.equals(edge.get("couldNotResolve"))) {
          continue;
        }
        final String resolved = projectPath(copy, (String) edge.get("resolved"));
        if (sources.contains(resolved)) {
          edges.add(map.get("source") + "->" + resolved);
        }
      }
    }
    return edges.stream().distinct().sorted().toList();
  }

  /** Maps a workspace link to the real file it points at, and leaves other paths as they are. */
  private static String projectPath(final Path copy, final String resolved) throws IOException {
    if (!resolved.startsWith(NODE_MODULES_PREFIX)) {
      return resolved;
    }
    final Path real = copy.resolve(resolved).toRealPath();
    return copy.toRealPath().relativize(real).toString().replace('\\', '/');
  }

  private record Fixture(String name, boolean typescript, List<Link> links) {}

  private record Link(String name, String directory) {}
}
