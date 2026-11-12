package dev.ward.infrastructure.dependency.gradle;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads the inventory from Gradle lockfiles, which is only right when the build locks every
 * configuration: {@code gradle.lockfile} lines are {@code group:name:version=conf1,conf2}, and
 * legacy {@code gradle/dependency-locks/<conf>.lockfile} files list coordinates per configuration.
 * A coordinate is direct when a build script of the module names it as a string or map notation or
 * through a version-catalog alias.
 */
final class GradleLockSource {
  private static final Logger LOGGER = Logger.getLogger(GradleLockSource.class.getName());
  private static final Pattern LOCK_ALL = Pattern.compile("lockAllConfigurations\\s*\\(\\s*\\)");
  private static final Pattern COORDINATE =
      Pattern.compile("[\"']([\\w.\\-]+):([\\w.\\-]+)(?::[^\"'\\s]*)?[\"']");
  private static final Pattern MAP_NOTATION =
      Pattern.compile(
          "group\\s*[:=]\\s*[\"']([^\"']+)[\"']\\s*,\\s*name\\s*[:=]\\s*[\"']([^\"']+)[\"']");
  private static final Pattern ALIAS = Pattern.compile("\\blibs\\.([A-Za-z0-9_.]+)");
  private static final Pattern LOCK_LINE =
      Pattern.compile("^([^:=\\s]+):([^:=\\s]+):([^=\\s]+)(?:=(.*))?$");

  private GradleLockSource() {}

  /**
   * Returns the inventory when the build locks every configuration and every project has a
   * lockfile, else empty so that the build tool resolves the project.
   */
  static Optional<DependencyInventory> read(final Project project) {
    final Path root = project.root();
    final List<String> modules = GradleModules.paths(root);
    if (!locksEverything(root, modules) || !modules.stream().allMatch(m -> hasLock(dir(root, m)))) {
      LOGGER.log(Level.FINE, "event=dependency-source source=gradle-lock usable=false");
      return Optional.empty();
    }
    final Map<String, Set<String>> catalog = catalog(root);
    final List<ModuleDependencies> result = new ArrayList<>();
    for (final String module : modules) {
      result.add(module(project, module, catalog));
    }
    final DependencyInventory inventory = new DependencyInventory(result);
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
      final String script = GradleModules.buildScript(dir);
      if (script != null) {
        scripts.add(dir.resolve(script));
      }
    }
    for (final String name : List.of("settings.gradle", "settings.gradle.kts")) {
      scripts.add(root.resolve(name));
    }
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
              .filter(
                  file ->
                      file.toString().endsWith(".kts")
                          || file.toString().endsWith(".gradle")
                          || file.toString().endsWith(".kt")
                          || file.toString().endsWith(".groovy"))
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

  private static boolean hasLock(final Path dir) {
    if (Files.isRegularFile(dir.resolve("gradle.lockfile"))) {
      return true;
    }
    return !lockfiles(dir.resolve("gradle").resolve("dependency-locks")).isEmpty();
  }

  private static List<Path> lockfiles(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(file -> file.toString().endsWith(".lockfile")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ModuleDependencies module(
      final Project project, final String module, final Map<String, Set<String>> catalog) {
    final Path dir = dir(project.root(), module);
    final Map<String, Set<String>> locked = locked(project, dir);
    final String scriptName = GradleModules.buildScript(dir);
    final String script = scriptName == null ? "" : LockFiles.join(module, scriptName);
    final Set<String> declared = declared(dir, scriptName, catalog);
    final String source =
        Files.isRegularFile(dir.resolve("gradle.lockfile"))
            ? LockFiles.join(module, "gradle.lockfile")
            : LockFiles.join(module, "gradle/dependency-locks");
    final ModuleBuilder builder = new ModuleBuilder(source, false);
    locked.forEach(
        (coordinate, configurations) -> {
          final String[] parts = coordinate.split(":", 3);
          final PackageId id = new PackageId(Ecosystem.MAVEN, parts[0] + ":" + parts[1], parts[2]);
          final DependencyScope scope = scope(configurations);
          builder.add(id, !id.version().endsWith("-SNAPSHOT")).scope(id, scope);
          if (declared.contains(id.name()) && !script.isEmpty()) {
            builder.direct(id, scope, script);
          }
        });
    return builder.build(module);
  }

  private static Map<String, Set<String>> locked(final Project project, final Path dir) {
    final Map<String, Set<String>> locked = new LinkedHashMap<>();
    try {
      final Path single = dir.resolve("gradle.lockfile");
      if (Files.isRegularFile(single)) {
        for (final String line : Files.readAllLines(single)) {
          entry(project, locked, line, null);
        }
      }
      for (final Path file : lockfiles(dir.resolve("gradle").resolve("dependency-locks"))) {
        final String configuration = file.getFileName().toString().replace(".lockfile", "");
        for (final String line : Files.readAllLines(file)) {
          entry(project, locked, line, configuration);
        }
      }
    } catch (IOException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          "gradle.lockfile",
          "it cannot be read",
          "check its permissions");
    }
    return locked;
  }

  private static void entry(
      final Project project,
      final Map<String, Set<String>> locked,
      final String line,
      final String configuration) {
    final String text = line.strip();
    if (text.isEmpty() || text.startsWith("#") || text.startsWith("empty=")) {
      return;
    }
    final Matcher matcher = LOCK_LINE.matcher(text);
    if (!matcher.matches()) {
      throw new DependencyInventoryException(
          project.root().toString(),
          "gradle.lockfile",
          "a line is not group:name:version=configurations",
          "run `gradle dependencies --write-locks`");
    }
    final String coordinate = matcher.group(1) + ":" + matcher.group(2) + ":" + matcher.group(3);
    final Set<String> configurations =
        locked.computeIfAbsent(coordinate, key -> new LinkedHashSet<>());
    if (configuration != null) {
      configurations.add(configuration);
    } else if (matcher.group(4) != null) {
      configurations.addAll(List.of(matcher.group(4).split(",")));
    }
  }

  /** Processors are development, test-only is test, runtime is runtime, compile-only provided. */
  private static DependencyScope scope(final Set<String> configurations) {
    DependencyScope scope = DependencyScope.DEVELOPMENT;
    boolean any = false;
    for (final String configuration : configurations) {
      any = true;
      scope = DependencyScope.widest(scope, scopeOf(configuration.toLowerCase(Locale.ROOT)));
    }
    return any ? scope : DependencyScope.RUNTIME;
  }

  private static DependencyScope scopeOf(final String name) {
    if (name.contains("annotationprocessor") || name.startsWith("kapt") || name.startsWith("ksp")) {
      return DependencyScope.DEVELOPMENT;
    }
    if (name.contains("test")) {
      return DependencyScope.TEST;
    }
    return name.endsWith("compileclasspath") || name.startsWith("compileonly")
        ? DependencyScope.PROVIDED
        : DependencyScope.RUNTIME;
  }

  /**
   * shortcut: a dependency declared through a function, a convention plugin or a platform looks
   * transitive here; Gradle's lockfile records no declarations, so only literal notations and
   * catalog aliases in the module's own script are direct.
   */
  private static Set<String> declared(
      final Path dir, final String scriptName, final Map<String, Set<String>> catalog) {
    final Set<String> names = new LinkedHashSet<>();
    if (scriptName == null) {
      return names;
    }
    final String text;
    try {
      text = Files.readString(dir.resolve(scriptName));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    final Matcher strings = COORDINATE.matcher(text);
    while (strings.find()) {
      names.add(strings.group(1) + ":" + strings.group(2));
    }
    final Matcher maps = MAP_NOTATION.matcher(text);
    while (maps.find()) {
      names.add(maps.group(1) + ":" + maps.group(2));
    }
    final Matcher aliases = ALIAS.matcher(text);
    while (aliases.find()) {
      names.addAll(resolveAlias(aliases.group(1), catalog));
    }
    return names;
  }

  /** Finds the catalog entry of {@code libs.<alias>}, trying shorter prefixes for accessors. */
  private static Set<String> resolveAlias(
      final String alias, final Map<String, Set<String>> catalog) {
    String candidate = alias;
    while (!candidate.isEmpty()) {
      final Set<String> found = catalog.get(candidate);
      if (found != null) {
        return found;
      }
      final int dot = candidate.lastIndexOf('.');
      candidate = dot < 0 ? "" : candidate.substring(0, dot);
    }
    return Set.of();
  }

  /** Returns the catalog libraries by dotted alias, with the {@code group:name} each stands for. */
  private static Map<String, Set<String>> catalog(final Path root) {
    final Map<String, Set<String>> libraries = new LinkedHashMap<>();
    final Path file = root.resolve("gradle").resolve("libs.versions.toml");
    if (!Files.isRegularFile(file)) {
      return libraries;
    }
    try {
      final TomlParseResult parsed = Toml.parse(Files.readString(file));
      final TomlTable table = parsed.getTable("libraries");
      if (parsed.hasErrors() || table == null) {
        return libraries;
      }
      for (final String key : table.keySet()) {
        module(table, key)
            .ifPresent(name -> libraries.put(key.replaceAll("[-_]", "."), Set.of(name)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return libraries;
  }

  private static Optional<String> module(final TomlTable libraries, final String key) {
    final String text = libraries.getString(key);
    if (text != null) {
      final String[] parts = text.split(":");
      return parts.length >= 2 ? Optional.of(parts[0] + ":" + parts[1]) : Optional.empty();
    }
    final TomlTable table = libraries.getTable(key);
    if (table == null) {
      return Optional.empty();
    }
    final String module = table.getString("module");
    if (module != null) {
      return Optional.of(module);
    }
    final String group = table.getString("group");
    final String name = table.getString("name");
    return group != null && name != null ? Optional.of(group + ":" + name) : Optional.empty();
  }

  private static Path dir(final Path root, final String module) {
    return module.isEmpty() ? root : root.resolve(module);
  }
}
