package dev.ward.domain.config;

import java.util.List;

/**
 * The options of an {@code api-compat} gate.
 *
 * @param base the Git ref the working tree is compared with, or {@code null} for the latest release
 *     tag
 * @param include the package prefixes that limit the API, empty for every package
 */
public record ApiCompatOptions(String base, List<String> include) {
  /** The options of a gate that sets none. */
  public static final ApiCompatOptions DEFAULTS = new ApiCompatOptions(null, List.of());

  /** Copies the prefixes. */
  public ApiCompatOptions {
    include = include == null ? List.of() : List.copyOf(include);
  }
}
