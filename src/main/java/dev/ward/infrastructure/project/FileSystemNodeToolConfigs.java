package dev.ward.infrastructure.project;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.NodeToolConfigs;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Finds a Node tool's config in the project root by the file names the tool reads. A file that
 * starts with Ward's marker is Ward's own and does not count.
 */
public final class FileSystemNodeToolConfigs implements NodeToolConfigs {
  private static final Logger LOGGER = Logger.getLogger(FileSystemNodeToolConfigs.class.getName());
  private static final String JEST = "jest";
  private static final String PACKAGE_JSON = "package.json";
  // The markers init writes in JavaScript and TOML configs.
  private static final List<String> WARD_MARKERS =
      List.of("// managed-by: ward", "# managed-by: ward");

  // The base file a layer renames the project's own config to; Ward's layer imports or extends it.
  private static final Map<String, String> BASE_GLOBS =
      Map.of("eslint", "eslint.base.*", "biome", "biome.base.{json,jsonc}");

  // Vitest prefers vitest.config over vite.config, so a Ward vitest.config would hide the user's.
  private static final Map<String, String> GLOBS =
      Map.of(
          "vitest",
          "{vitest.config.*,vite.config.*}",
          JEST,
          "jest.config.*",
          "stryker",
          "{stryker,.stryker}.{conf,config}.*",
          "eslint",
          "{eslint.config.*,.eslintrc*}",
          "dependency-cruiser",
          ".dependency-cruiser.{json,js,cjs,mjs,ts,cts,mts}",
          "biome",
          "{biome,.biome}.{json,jsonc}");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemNodeToolConfigs() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<Path> userConfig(final Path root, final String gate) {
    final String glob = GLOBS.get(gate);
    if (glob == null) {
      return Optional.empty();
    }
    final Optional<Path> file = firstFile(root, glob, false);
    if (file.isEmpty() && JEST.equals(gate) && jestInManifest(root)) {
      return Optional.of(root.resolve(PACKAGE_JSON));
    }
    file.ifPresent(
        found ->
            LOGGER.log(
                Level.FINE,
                () -> "event=node-user-config gate=" + gate + " file=" + found.getFileName()));
    return file;
  }

  @Override
  public Optional<Path> wardConfig(final Path root, final String gate) {
    final String glob = GLOBS.get(gate);
    return glob == null ? Optional.empty() : firstFile(root, glob, true);
  }

  @Override
  public Optional<Path> baseConfig(final Path root, final String gate) {
    final String glob = BASE_GLOBS.get(gate);
    return glob == null ? Optional.empty() : firstFile(root, glob, false);
  }

  /**
   * Returns the first file in {@code root} matching {@code glob} whose Ward ownership is {@code
   * owned}.
   */
  private static Optional<Path> firstFile(final Path root, final String glob, final boolean owned) {
    if (!Files.isDirectory(root)) {
      return Optional.empty();
    }
    final List<Path> matches = new ArrayList<>();
    try (DirectoryStream<Path> files = Files.newDirectoryStream(root, glob)) {
      files.forEach(matches::add);
      matches.sort(null);
      for (final Path file : matches) {
        if (Files.isRegularFile(file) && wardOwned(Files.readString(file)) == owned) {
          return Optional.of(file);
        }
      }
    } catch (IOException e) {
      throw new WardException("Could not read " + root + ": " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  private static boolean wardOwned(final String content) {
    return WARD_MARKERS.stream().anyMatch(content::startsWith);
  }

  private static boolean jestInManifest(final Path root) {
    return NodeProjectInspection.manifest(root).containsKey(JEST);
  }
}
