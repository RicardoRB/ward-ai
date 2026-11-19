package dev.ward.infrastructure.project.dotnet;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads .NET projects from build files only; {@code dotnet} is never run. A solution ({@code .sln}
 * or {@code .slnx}) or a {@code .csproj} makes a C# project, and a solution owns the projects it
 * lists.
 */
public final class DotnetProjectInspection {
  private static final String PROJECT_EXTENSION = ".csproj";
  private static final List<String> SOLUTION_EXTENSIONS = List.of(".sln", ".slnx");
  // A .sln writes the path as "..\A\A.csproj", a .slnx as Path="A/A.csproj".
  private static final Pattern LISTED_PROJECT =
      Pattern.compile("[\"']([^\"']+\\.csproj)[\"']", Pattern.CASE_INSENSITIVE);

  private DotnetProjectInspection() {}

  /** Returns the C# project at {@code dir}, whose entries are {@code names}, if it has a marker. */
  public static Optional<Project> project(final Path dir, final List<String> names) {
    final boolean marked =
        names.stream().anyMatch(name -> isSolution(name) || name.endsWith(PROJECT_EXTENSION));
    return marked
        ? Optional.of(new Project(dir, Language.CSHARP, BuildTool.DOTNET, Framework.NONE))
        : Optional.empty();
  }

  /** Returns whether a solution in {@code aggregator} lists the project in {@code candidate}. */
  public static boolean isMember(final Path aggregator, final Path candidate) {
    return members(aggregator).contains(candidate.toAbsolutePath().normalize());
  }

  /** Returns the directories of the projects that the solutions in {@code dir} list. */
  public static Set<Path> members(final Path dir) {
    final Set<Path> members = new HashSet<>();
    try (Stream<Path> files = Files.list(dir)) {
      for (final Path solution : files.filter(
          file -> isSolution(file.getFileName().toString())
      ).toList()) {
        final Matcher listed = LISTED_PROJECT.matcher(Files.readString(solution));
        while (listed.find()) {
          final Path project = dir.resolve(listed.group(1).replace('\\', '/')).normalize();
          members.add(project.toAbsolutePath().getParent());
        }
      }
    } catch (IOException e) {
      return Set.of();
    }
    return members;
  }

  private static boolean isSolution(final String name) {
    return SOLUTION_EXTENSIONS.stream().anyMatch(name::endsWith);
  }
}
