package dev.ward.domain.config;

import java.util.List;

/**
 * The options of an {@code api-compat} gate.
 *
 * @param base the Git ref the working tree is compared with, or {@code null} for the latest release
 *     tag
 * @param include the package prefixes that limit the API, empty for every package
 * @param modules the module directories whose API is compared, empty for every module
 */
public record ApiCompatOptions(String base, List<String> include, List<String> modules) {
  /** The options of a gate that sets none. */
  public static final ApiCompatOptions DEFAULTS = new ApiCompatOptions(null, List.of(), List.of());

  /** Copies the prefixes and the modules. */
  public ApiCompatOptions {
    include = include == null ? List.of() : List.copyOf(include);
    modules = modules == null ? List.of() : List.copyOf(modules);
  }

  /** Creates options that name no module. */
  public ApiCompatOptions(final String base, final List<String> include) {
    this(base, include, List.of());
  }
}
