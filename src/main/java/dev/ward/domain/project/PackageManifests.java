package dev.ward.domain.project;

import java.nio.file.Path;

/** Outbound contract for reading a Node project's {@code package.json}. */
@FunctionalInterface
public interface PackageManifests {
  /** Returns the manifest at {@code root}, or {@link PackageManifest#EMPTY} when unreadable. */
  PackageManifest read(Path root);
}
