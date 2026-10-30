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
   * Returns whether {@code root} holds a source below its own directory, in a subfolder such as
   * {@code src/} or {@code app/}. A source file directly in {@code root} does not count, so a
   * package with only root-level configuration has none.
   */
  default boolean hasSourceTree(final Path root) {
    return hasSources(root);
  }
}
