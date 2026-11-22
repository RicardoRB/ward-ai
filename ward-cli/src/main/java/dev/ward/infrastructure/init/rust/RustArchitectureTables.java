package dev.ward.infrastructure.init.rust;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.rust.CargoWorkspaces;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;

/** The Rust architecture tables: the {@code src} directory of the crate and of each member. */
public final class RustArchitectureTables implements ArchitectureTables {
  private static final String SRC = "src";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public RustArchitectureTables() {}

  @Override
  public List<String> cycleRoots(final Project project) {
    final Path root = project.root();
    return Stream.concat(Stream.of(root), CargoWorkspaces.members(root).stream())
        .map(crate -> crate.resolve(SRC))
        .filter(Files::isDirectory)
        .map(source -> root.relativize(source).toString().replace('\\', '/'))
        .toList();
  }

  @Override
  public List<String> independenceModules(final Project project, final String style) {
    return List.of();
  }
}
