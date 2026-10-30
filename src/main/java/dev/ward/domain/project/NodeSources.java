package dev.ward.domain.project;

import java.nio.file.Path;

/** Outbound contract for finding whether a Node package holds JavaScript sources of its own. */
@FunctionalInterface
public interface NodeSources {
  /**
   * Returns whether {@code root} holds {@code .js}, {@code .jsx}, {@code .mjs} or {@code .cjs}
   * sources outside dependencies, build output and projects nested below it.
   */
  boolean hasSources(Path root);

  /**
   * Returns whether {@code root} holds sources under its {@code src} directory, which is where a
   * Turborepo member keeps the code its gates check. A member with configuration only has none.
   */
  default boolean hasSourceTree(final Path root) {
    return hasSources(root);
  }
}
