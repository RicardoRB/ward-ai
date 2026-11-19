package dev.ward.infrastructure.dependency.cargo;

import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * What a module's {@code Cargo.toml} declares: dependencies and build dependencies are runtime,
 * dev dependencies are test, in the plain tables and in {@code [target.*]} tables.
 *
 * @param path the module path relative to the project root
 * @param manifest the project-relative path of its Cargo.toml
 * @param declared the normalized crate names it declares, with their widest scope
 */
record CargoManifest(String path, String manifest, Map<String, DependencyScope> declared) {
  private static final Map<String, DependencyScope> TABLES =
      Map.of(
          "dependencies",
          DependencyScope.RUNTIME,
          "build-dependencies",
          DependencyScope.RUNTIME,
          "dev-dependencies",
          DependencyScope.TEST
      );

  /** Copies the declared names. */
  CargoManifest {
    declared = Map.copyOf(declared);
  }

  /**
   * Reads the Cargo.toml of {@code dir}, or an empty manifest when there is none.
   *
   * @throws DependencyInventoryException if it is not valid TOML
   */
  static CargoManifest read(final Path root, final Path dir, final Project project) {
    final String path = LockFiles.relative(root, dir);
    final String manifest = LockFiles.join(path, "Cargo.toml");
    final Path file = dir.resolve("Cargo.toml");
    if (!Files.isRegularFile(file)) {
      return new CargoManifest(path, manifest, Map.of());
    }
    try {
      final TomlParseResult parsed = Toml.parse(Files.readString(file));
      if (parsed.hasErrors()) {
        throw new DependencyInventoryException(
            project.root().toString(),
            manifest,
            "it is not valid TOML",
            "fix the TOML syntax"
        );
      }
      final Map<String, DependencyScope> declared = new LinkedHashMap<>();
      collect(parsed, declared);
      final TomlTable targets = parsed.getTable("target");
      if (targets != null) {
        targets.keySet().forEach(
            key -> collect(targets.getTableOrEmpty(java.util.List.of(key)), declared)
        );
      }
      return new CargoManifest(path, manifest, declared);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          manifest,
          "it cannot be read",
          "check its permissions",
          e
      );
    }
  }

  private static void collect(final TomlTable table, final Map<String, DependencyScope> declared) {
    TABLES.forEach(
        (name, scope) -> {
          final TomlTable dependencies = table.getTable(name);
          if (dependencies != null) {
            dependencies
                .keySet()
                .forEach(
                    key -> declared.merge(
                        realName(dependencies, key),
                        scope,
                        DependencyScope::widest
                    )
                );
          }
        }
    );
  }

  /** Returns the normalized crate a key names: its {@code package} when it is renamed. */
  private static String realName(final TomlTable dependencies, final String key) {
    final Object value = dependencies.get(java.util.List.of(key));
    final String renamed =
        value instanceof TomlTable table && table.isString("package")
            ? table.getString("package")
            : key;
    return CargoLockfile.normalize(renamed);
  }
}
