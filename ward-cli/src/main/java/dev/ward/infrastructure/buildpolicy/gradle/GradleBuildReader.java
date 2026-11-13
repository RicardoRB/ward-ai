package dev.ward.infrastructure.buildpolicy.gradle;

import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.ToolVersion;
import dev.ward.infrastructure.buildpolicy.BuildPaths;
import dev.ward.infrastructure.buildpolicy.WrapperVersions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads the Gradle scripts, the version catalog and the wrapper properties of a directory. */
public final class GradleBuildReader implements BuildModelReader {
  private static final Logger LOGGER = Logger.getLogger(GradleBuildReader.class.getName());
  private static final List<String> SCRIPTS = List.of("build.gradle", "build.gradle.kts");
  private static final List<String> SETTINGS = List.of("settings.gradle", "settings.gradle.kts");
  private static final String CATALOG = "gradle/libs.versions.toml";
  private static final String WRAPPER = "gradle/wrapper/gradle-wrapper.properties";

  @Override
  public List<String> modules(final Path root, final String dir) {
    final List<String> modules = new ArrayList<>();
    for (final String name : SETTINGS) {
      final Path file = BuildPaths.file(root, dir, name);
      if (Files.isRegularFile(file)) {
        GradleScripts.includes(BuildPaths.read(file, BuildPaths.relative(dir, name))).stream()
            .map(module -> BuildPaths.relative(dir, module))
            .forEach(modules::add);
      }
    }
    return modules;
  }

  @Override
  public BuildModel read(final Path root, final String dir) {
    final boolean locked = Files.isRegularFile(BuildPaths.file(root, dir, "gradle.lockfile"));
    final List<Declaration> declarations = new ArrayList<>();
    for (final String name : List.of(SCRIPTS, SETTINGS).stream().flatMap(List::stream).toList()) {
      final Path file = BuildPaths.file(root, dir, name);
      if (Files.isRegularFile(file)) {
        final String shown = BuildPaths.relative(dir, name);
        LOGGER.log(Level.FINE, "event=build-policy-read-gradle file={0}", shown);
        declarations.addAll(GradleScripts.read(BuildPaths.read(file, shown), shown, locked));
      }
    }
    final Path catalog = BuildPaths.file(root, dir, CATALOG);
    if (Files.isRegularFile(catalog)) {
      declarations.addAll(VersionCatalog.read(catalog, BuildPaths.relative(dir, CATALOG), locked));
    }
    final List<ToolVersion> tools = new ArrayList<>();
    final String wrapper = BuildPaths.relative(dir, WRAPPER);
    WrapperVersions.read(root.resolve(wrapper), wrapper, "gradle", "gradle-").ifPresent(tools::add);
    return new BuildModel(declarations, tools);
  }
}
