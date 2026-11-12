package dev.ward.domain.agent;

import java.util.Objects;

/**
 * A path a patch affects, exactly as the patch named it.
 *
 * @param path the path as written in the patch
 * @param kind how the patch affects the path
 */
public record PatchTarget(String path, Kind kind) {

  /** How a patch affects a path. */
  public enum Kind {
    WRITE,
    DELETE,
    MOVE_FROM,
    MOVE_TO
  }

  /**
   * Validates the target.
   *
   * @throws NullPointerException if the path or kind is missing
   */
  public PatchTarget {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(kind, "kind");
  }
}
