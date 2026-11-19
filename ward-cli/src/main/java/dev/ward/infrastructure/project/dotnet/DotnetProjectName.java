package dev.ward.infrastructure.project.dotnet;

import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectNameReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Names a .NET project after its first solution or project file, without the extension. */
public final class DotnetProjectName implements ProjectNameReader {
  private static final List<String> EXTENSIONS =
      List.of(".sln", ".slnx", ".csproj");

  /** Creates the reader. */
  @Inject
  public DotnetProjectName() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<String> read(final Project project) {
    try (Stream<Path> files = Files.list(project.root())) {
      final List<String> names =
          files.map(file -> String.valueOf(file.getFileName())).sorted().toList();
      for (final String extension : EXTENSIONS) {
        final Optional<String> found =
            names.stream()
                .filter(name -> name.endsWith(extension))
                .findFirst()
                .map(name -> name.substring(0, name.length() - extension.length()));
        if (found.isPresent()) {
          return found;
        }
      }
      return Optional.empty();
    } catch (IOException exception) {
      return Optional.empty();
    }
  }
}
