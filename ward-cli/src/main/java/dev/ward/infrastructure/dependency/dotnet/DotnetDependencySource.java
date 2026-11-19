package dev.ward.infrastructure.dependency.dotnet;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads the inventory of a C# project without running {@code dotnet}: one module per project file,
 * from the {@code packages.lock.json} beside it, or else from {@code obj/project.assets.json}. A
 * project that declares packages and has neither is an error that names {@code dotnet restore}. A
 * test project ({@code IsTestProject} or the test SDK) has test scope; every other package is a
 * runtime package.
 */
public final class DotnetDependencySource implements DependencySource {
  private static final Logger LOGGER = Logger.getLogger(DotnetDependencySource.class.getName());
  private static final String LOCK = "packages.lock.json";
  private static final String ASSETS = "obj/project.assets.json";
  private static final String RESTORE = "run `dotnet restore --use-lock-file`";

  private final JsonCodec codec;

  /** Creates the source; Dagger injects the codec. */
  @Inject
  public DotnetDependencySource(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public DependencyInventory read(final Project project) {
    final Path root = project.root().toAbsolutePath().normalize();
    final List<ModuleDependencies> modules = new ArrayList<>();
    for (final Path file : DotnetProjects.projectFiles(project)) {
      module(project, root, file).ifPresent(modules::add);
    }
    if (modules.isEmpty()) {
      modules.add(new ModuleDependencies("", List.of()));
    }
    LOGGER.log(Level.FINE, () -> "event=dependency-source source=dotnet modules=" + modules.size());
    return new DependencyInventory(modules);
  }

  private Optional<ModuleDependencies> module(
      final Project project,
      final Path root,
      final Path projectFile
  ) {
    final Path directory = projectFile.getParent();
    final String declaredIn = LockFiles.relative(root, projectFile);
    final String text = read(project, projectFile, declaredIn, "check its permissions");
    final Optional<String> lockName = LockFiles.first(directory, List.of(LOCK, ASSETS));
    if (lockName.isEmpty()) {
      if (text.contains("<PackageReference")) {
        throw new DependencyInventoryException(
            project.root().toString(),
            declaredIn,
            "the project declares packages and has no packages.lock.json or project.assets.json",
            RESTORE
        );
      }
      return Optional.empty();
    }
    final Path lock = directory.resolve(lockName.get());
    final String label = LockFiles.relative(root, lock);
    final boolean assets = lockName.get().equals(ASSETS);
    final DependencyScope scope = testProject(text) ? DependencyScope.TEST : DependencyScope.RUNTIME;
    final ModuleBuilder builder = new ModuleBuilder(label, false);
    entries(project, lock, label, assets)
        .forEach(
            entry -> {
              builder.add(entry.id(), true).scope(entry.id(), scope);
              entry.requires().forEach(child -> builder.edge(entry.id(), child));
              if (entry.direct()) {
                builder.direct(entry.id(), scope, declaredIn);
              }
            }
        );
    final Path relative = root.relativize(directory);
    return Optional.of(builder.build(relative.toString().replace('\\', '/')));
  }

  private List<NugetLockfiles.Entry> entries(
      final Project project,
      final Path lock,
      final String label,
      final boolean assets
  ) {
    final String text = read(project, lock, label, "check its permissions");
    try {
      return assets ? NugetLockfiles.assets(text, codec) : NugetLockfiles.lock(text, codec);
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          e.getMessage(),
          RESTORE,
          e
      );
    }
  }

  private static boolean testProject(final String projectText) {
    return projectText.contains("<IsTestProject>true")
        || projectText.contains("Microsoft.NET.Test.Sdk");
  }

  private static String read(
      final Project project,
      final Path file,
      final String label,
      final String fix
  ) {
    try {
      return Files.readString(file);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          "it cannot be read",
          fix,
          e
      );
    }
  }
}
