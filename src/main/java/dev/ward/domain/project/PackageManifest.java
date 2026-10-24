package dev.ward.domain.project;

import java.util.Map;

/**
 * The {@code package.json} facts init needs.
 *
 * @param dependencies the packages declared in {@code dependencies} and {@code devDependencies}, by
 *     name, with their declared version ranges
 * @param type the {@code "type"} field, or {@code null} when absent
 * @param packageManager the {@code packageManager} field, or {@code null} when absent
 */
public record PackageManifest(
    Map<String, String> dependencies, String type, String packageManager) {
  /** A project without a readable {@code package.json}. */
  public static final PackageManifest EMPTY = new PackageManifest(Map.of(), null, null);

  /** Copies the dependencies. */
  public PackageManifest {
    dependencies = Map.copyOf(dependencies);
  }

  /**
   * Returns whether {@code name} is declared in {@code dependencies} or {@code devDependencies}.
   */
  public boolean declares(final String name) {
    return dependencies.containsKey(name);
  }

  /** Returns whether {@code "type"} is {@code "module"}, so {@code .js} files are ES modules. */
  public boolean module() {
    return "module".equals(type);
  }
}
