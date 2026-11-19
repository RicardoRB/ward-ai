package dev.ward.infrastructure.analysis.rust;

import dev.ward.analysis.domain.rust.RustCrate;
import dev.ward.analysis.domain.rust.RustProjectLayout;
import dev.ward.infrastructure.project.rust.CargoWorkspaces;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads the crates of a Cargo project root from its manifests: the root package and each workspace
 * member, each with {@code src} as its source directory. Reads only: no process starts.
 */
public final class RustLayouts {
  private static final String SOURCES = "src";

  private RustLayouts() {}

  /** Tells whether the root holds a Cargo manifest. */
  public static boolean isRustProject(final Path root) {
    return root.resolve(CargoWorkspaces.MANIFEST).toFile().isFile();
  }

  /** Reads the layout of the root: its own package, then each member of its workspace. */
  public static RustProjectLayout read(final Path root) {
    final List<RustCrate> crates = new ArrayList<>();
    Stream.concat(Stream.of(root), CargoWorkspaces.members(root).stream())
        .forEach(directory -> crate(root, directory).ifPresent(crates::add));
    return new RustProjectLayout(crates);
  }

  private static Optional<RustCrate> crate(final Path root, final Path directory) {
    return Optional.ofNullable(CargoWorkspaces.manifest(directory).getString("package.name"))
        .filter(name -> !name.isBlank())
        .map(
            name ->
                new RustCrate(
                    name.replace('-', '_'),
                    root.relativize(directory.resolve(SOURCES)).toString().replace('\\', '/')
                )
        );
  }
}
