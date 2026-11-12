package dev.ward.infrastructure.dependency.gradle;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the inventory from Gradle lockfiles, which is only right when the build locks every
 * configuration and every project has a lockfile. Otherwise the build tool resolves the project.
 */
final class GradleLockSource {
  private static final Logger LOGGER = Logger.getLogger(GradleLockSource.class.getName());
  private static final Pattern LOCK_ALL = Pattern.compile("lockAllConfigurations\\s*\\(\\s*\\)");
  private static final List<String> CONVENTION_SUFFIXES =
      List.of(".kts", ".gradle", ".kt", ".groovy");

  private GradleLockSource() {}

  /**
   * Returns the inventory when the build locks every configuration and every project has a
   * lockfile, else empty so that the build tool resolves the project.
   */
  static Optional<DependencyInventory> read(final Project project) {
    final Path root = project.root();
    final List<String> modules = GradleModules.paths(root);
    if (!locksEverything(root, modules)
        || !modules.stream().allMatch(module -> GradleLockfiles.exists(dir(root, module)))) {
      LOGGER.log(Level.FINE, "event=dependency-source source=gradle-lock usable=false");
      return Optional.empty();
    }
    final GradleDeclarations declarations = new GradleDeclarations(root);
    final DependencyInventory inventory =
        new DependencyInventory(
            modules.stream().map(module -> module(project, module, declarations)).toList());
    LOGGER.log(
        Level.FINE,
        () ->
            "event=dependency-source source=gradle-lock modules="
                + inventory.modules().size()
                + " entries="
                + inventory.entryCount());
    return Optional.of(inventory);
  }

  private static boolean locksEverything(final Path root, final List<String> modules) {
    final List<Path> scripts = new ArrayList<>();
    for (final String module : modules) {
      final Path dir = dir(root, module);
      Optional.ofNullable(GradleModules.buildScript(dir)).map(dir::resolve).ifPresent(scripts::add);
    }
    scripts.add(root.resolve("settings.gradle"));
    scripts.add(root.resolve("settings.gradle.kts"));
    scripts.addAll(conventionSources(root));
    return scripts.stream().anyMatch(file -> Files.isRegularFile(file) && contains(file));
  }

  /** The Gradle and Kotlin sources of buildSrc and build-logic, where conventions often live. */
  private static List<Path> conventionSources(final Path root) {
    final List<Path> sources = new ArrayList<>();
    for (final String name : List.of("buildSrc", "build-logic")) {
      final Path dir = root.resolve(name);
      if (Files.isDirectory(dir)) {
        try (Stream<Path> walk = Files.walk(dir)) {
          walk.filter(Files::isRegularFile)
              .filter(file -> CONVENTION_SUFFIXES.stream().anyMatch(file.toString()::endsWith))
              .forEach(sources::add);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
    return sources;
  }

  private static boolean contains(final Path file) {
    try {
      return LOCK_ALL.matcher(Files.readString(file)).find();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ModuleDependencies module(
      final Project project, final String module, final GradleDeclarations declarations) {
    final Path dir = dir(project.root(), module);
    final String scriptName = GradleModules.buildScript(dir);
    final Set<String> declared =
        scriptName == null ? Set.of() : declarations.declared(dir.resolve(scriptName));
    final String script = scriptName == null ? "" : GradleModules.join(module, scriptName);
    final ModuleBuilder builder = new ModuleBuilder(GradleLockfiles.source(dir, module), false);
    GradleLockfiles.read(project.root().toString(), dir)
        .forEach(
            (coordinate, configurations) ->
                add(builder, coordinate, configurations, declared, script));
    return builder.build(module);
  }

  private static void add(
      final ModuleBuilder builder,
      final String coordinate,
      final Set<String> configurations,
      final Set<String> declared,
      final String script) {
    final String[] parts = coordinate.split(":", 3);
    final PackageId id = new PackageId(Ecosystem.MAVEN, parts[0] + ":" + parts[1], parts[2]);
    final DependencyScope scope = GradleLockfiles.scope(configurations);
    builder.add(id, !id.version().endsWith("-SNAPSHOT")).scope(id, scope);
    if (declared.contains(id.name()) && !script.isEmpty()) {
      builder.direct(id, scope, script);
    }
  }

  private static Path dir(final Path root, final String module) {
    return module.isEmpty() ? root : root.resolve(module);
  }
}
