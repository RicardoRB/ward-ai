package dev.ward.analysis.domain.node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What a Node project declares about its module names, read from its manifests and TypeScript
 * configs. Every path is project-root-relative with {@code /} separators, and the root is {@code
 * ""}.
 *
 * @param files the TypeScript and JavaScript files of the run
 * @param tsconfigs the TypeScript configs, each covering the files under its directory
 * @param packages the workspace packages, the root package included when it has a name
 */
public record NodeProjectLayout(
    Set<String> files, List<NodeTsConfig> tsconfigs, List<NodePackage> packages) {

  /**
   * Copies the collections.
   *
   * @throws NullPointerException if a field is missing
   */
  public NodeProjectLayout {
    Objects.requireNonNull(files, "files");
    Objects.requireNonNull(tsconfigs, "tsconfigs");
    Objects.requireNonNull(packages, "packages");
    files = Set.copyOf(files);
    tsconfigs = List.copyOf(tsconfigs);
    packages = List.copyOf(packages);
  }

  /**
   * One TypeScript config's effective module options, with inherited {@code extends} values merged.
   *
   * @param directory the directory of the config
   * @param baseUrl the {@code baseUrl}, or null when none applies
   * @param pathsBase the directory that {@code paths} targets are relative to, or null
   * @param paths the {@code paths} patterns and their targets, in declaration order
   */
  public record NodeTsConfig(
      String directory, String baseUrl, String pathsBase, Map<String, List<String>> paths) {

    /**
     * Copies the paths.
     *
     * @throws NullPointerException if the directory or paths are missing
     */
    public NodeTsConfig {
      Objects.requireNonNull(directory, "directory");
      Objects.requireNonNull(paths, "paths");
      paths = Collections.unmodifiableMap(new LinkedHashMap<>(paths));
    }
  }

  /**
   * A workspace package read from its package.json.
   *
   * @param name the package name
   * @param directory the directory of the package
   * @param exports the {@code exports} entries by subpath, each with its conditions in order
   * @param types the {@code types} field, or null
   * @param module the {@code module} field, or null
   * @param main the {@code main} field, or null
   */
  public record NodePackage(
      String name,
      String directory,
      Map<String, List<ExportEntry>> exports,
      String types,
      String module,
      String main) {

    /**
     * Copies the exports.
     *
     * @throws NullPointerException if the name or directory is missing
     */
    public NodePackage {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(directory, "directory");
      Objects.requireNonNull(exports, "exports");
      exports = Collections.unmodifiableMap(new LinkedHashMap<>(exports));
    }
  }

  /**
   * One {@code exports} target and the nested conditions that select it, outermost first.
   *
   * @param conditions the condition names that must all hold, in order
   * @param target the target path, relative to the package directory
   */
  public record ExportEntry(List<String> conditions, String target) {

    /**
     * Copies the conditions.
     *
     * @throws NullPointerException if the target is missing
     */
    public ExportEntry {
      Objects.requireNonNull(target, "target");
      conditions = List.copyOf(conditions);
    }
  }
}
