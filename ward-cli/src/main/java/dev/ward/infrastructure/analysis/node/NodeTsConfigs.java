package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.node.NodeProjectLayout.NodePackage;
import dev.ward.analysis.domain.node.NodeProjectLayout.NodeTsConfig;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.project.node.NodeProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads the TypeScript configs of a project with their {@code extends} chains applied. */
final class NodeTsConfigs {
  private static final Logger LOGGER = Logger.getLogger(NodeTsConfigs.class.getName());
  private static final String EXTENDS = "extends";
  private static final String COMPILER_OPTIONS = "compilerOptions";
  private static final String BASE_URL = "baseUrl";
  private static final String PATHS = "paths";
  private static final int MAX_EXTENDS_DEPTH = 10;

  private NodeTsConfigs() {}

  /** The TypeScript config of every directory that holds a file, or an ancestor of one. */
  static List<NodeTsConfig> of(
      final Path root, final Set<String> files, final List<NodePackage> packages) {
    final List<NodeTsConfig> configs = new ArrayList<>();
    for (final String directory : directoriesOf(files)) {
      final Path config = root.resolve(directory).resolve(NodeProjectInspection.TSCONFIG);
      if (Files.isRegularFile(config)) {
        final Effective effective = load(root, config, packages, 0);
        configs.add(
            new NodeTsConfig(
                directory, effective.baseUrl(), effective.pathsBase(), effective.paths()));
      }
    }
    return configs;
  }

  /** The directories that hold the files and each of their ancestors, the root included. */
  private static Set<String> directoriesOf(final Set<String> files) {
    final Set<String> directories = new TreeSet<>();
    for (final String file : files) {
      String directory = parent(file);
      while (directories.add(directory) && !directory.isEmpty()) {
        directory = parent(directory);
      }
    }
    return directories;
  }

  private static String parent(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }

  /** A config's options after {@code extends} is applied: root-relative and inherited. */
  private record Effective(String baseUrl, String pathsBase, Map<String, List<String>> paths) {
    static Effective empty() {
      return new Effective(null, null, Map.of());
    }
  }

  private static Effective load(
      final Path root, final Path config, final List<NodePackage> packages, final int depth) {
    final Map<?, ?> json = read(config);
    final Effective parent = inherited(root, config, json, packages, depth);
    final Map<?, ?> options = json.get(COMPILER_OPTIONS) instanceof Map<?, ?> map ? map : Map.of();
    final String baseUrl =
        options.get(BASE_URL) instanceof String value
            ? NodeLayouts.relative(root, directoryOf(config).resolve(value))
            : parent.baseUrl();
    if (!(options.get(PATHS) instanceof Map<?, ?> declared)) {
      return new Effective(baseUrl, parent.pathsBase(), parent.paths());
    }
    final String pathsBase =
        baseUrl != null ? baseUrl : NodeLayouts.relative(root, directoryOf(config));
    return new Effective(baseUrl, pathsBase, targets(declared));
  }

  private static Effective inherited(
      final Path root,
      final Path config,
      final Map<?, ?> json,
      final List<NodePackage> packages,
      final int depth) {
    if (!(json.get(EXTENDS) instanceof String extended) || depth >= MAX_EXTENDS_DEPTH) {
      return Effective.empty();
    }
    final Optional<Path> base =
        NodeExtendsTargets.resolve(root, directoryOf(config), extended, packages);
    if (base.isEmpty()) {
      if (LOGGER.isLoggable(Level.WARNING)) {
        LOGGER.log(
            Level.WARNING,
            "Skipping an unresolved tsconfig extends in {0}",
            NodeLayouts.relative(root, config));
      }
      return Effective.empty();
    }
    return load(root, base.get(), packages, depth + 1);
  }

  private static Map<String, List<String>> targets(final Map<?, ?> declared) {
    final Map<String, List<String>> paths = new LinkedHashMap<>();
    declared.forEach(
        (pattern, value) -> {
          if (value instanceof List<?> list) {
            paths.put(
                String.valueOf(pattern),
                list.stream().filter(String.class::isInstance).map(String.class::cast).toList());
          }
        });
    return paths;
  }

  /** The directory of a config file; a file always has one, so the file itself is the fallback. */
  private static Path directoryOf(final Path file) {
    return Objects.requireNonNullElse(file.getParent(), file);
  }

  /** Reads a JSON-with-comments file as a map, or an empty map with a warning. */
  private static Map<?, ?> read(final Path file) {
    try {
      final JsonCodec codec = new ForyJsonCodec();
      final Object parsed = codec.fromJson(JsoncText.plain(Files.readString(file)), Object.class);
      return parsed instanceof Map<?, ?> map ? map : Map.of();
    } catch (IOException | JsonCodecException | IllegalArgumentException e) {
      if (LOGGER.isLoggable(Level.WARNING)) {
        LOGGER.log(
            Level.WARNING,
            "Skipping an unreadable tsconfig or package manifest: {0}",
            file.getFileName());
      }
      return Map.of();
    }
  }
}
