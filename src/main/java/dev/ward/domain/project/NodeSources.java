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
}
