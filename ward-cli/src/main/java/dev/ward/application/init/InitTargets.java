package dev.ward.application.init;

import dev.ward.application.project.ProjectDetector;
import dev.ward.application.project.ProjectNotFoundException;
import dev.ward.domain.config.WardException;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The projects one init run may configure.
 *
 * @param projects the resolved projects, or every candidate when {@code choiceRequired}
 * @param discovered whether the projects come from discovery below the start directory
 * @param choiceRequired whether discovery found several candidates the user must choose from
 */
public record InitTargets(
    List<DiscoveredProject> projects, boolean discovered, boolean choiceRequired) {
  private static final Logger LOGGER = Logger.getLogger(InitTargets.class.getName());

  /** Copies {@code projects} so the targets stay immutable. */
  public InitTargets {
    projects = List.copyOf(projects);
  }

  /**
   * Resolves the projects for {@code start}: the {@code explicit} directories when given, else the
   * supported project containing {@code start}, else the ones discovered below it; {@code
   * supported} says which projects init can configure.
   *
   * @throws WardException if an explicit directory is not a supported project root, or if nothing
   *     supported is found and {@code start} is inside a project of another toolchain
   * @throws ProjectNotFoundException if no supported project is found
   */
  static InitTargets resolve(
      final ProjectDetector detector,
      final Path start,
      final List<String> explicit,
      final Predicate<Project> supported,
      final WorkspaceMembers members) {
    final Path base = start.toAbsolutePath().normalize();
    if (!explicit.isEmpty()) {
      log("explicit", explicit.size());
      return new InitTargets(
          explicit.stream().map(dir -> named(detector, base, dir, supported)).toList(),
          false,
          false);
    }
    final Optional<Project> containing = detector.detect(base);
    if (containing.filter(supported).isPresent()) {
      final Project project = containing.get();
      final List<DiscoveredProject> own = ownLanguageMembers(project, supported, members);
      log("containing", 1 + own.size());
      return new InitTargets(
          Stream.concat(Stream.of(new DiscoveredProject(project, 0)), own.stream()).toList(),
          false,
          false);
    }
    final List<DiscoveredProject> discovered =
        detector.discover(base).stream()
            .filter(candidate -> supported.test(candidate.project()))
            .filter(candidate -> candidate.project().root().startsWith(base))
            .toList();
    log("discovered", discovered.size());
    if (discovered.isEmpty()) {
      // No supported project below: an unsupported containing project must not get JVM files.
      throw containing
          .filter(InitToolchains::unknown)
          .<RuntimeException>map(project -> new WardException(InitToolchains.unsupported(project)))
          .orElseGet(ProjectNotFoundException::new);
    }
    return new InitTargets(discovered, true, discovered.size() > 1);
  }

  /**
   * Returns the members of a Turborepo root that hold another language's build file: init
   * configures each one as its own project. Other roots have none.
   */
  private static List<DiscoveredProject> ownLanguageMembers(
      final Project root, final Predicate<Project> supported, final WorkspaceMembers members) {
    if (!root.taskRunner().isRootRunner()) {
      return List.of();
    }
    return members.ownLanguageMembers(root.root()).stream()
        .map(WorkspaceMember::project)
        .filter(supported)
        .map(project -> new DiscoveredProject(project, 0))
        .toList();
  }

  private static DiscoveredProject named(
      final ProjectDetector detector,
      final Path base,
      final String name,
      final Predicate<Project> supported) {
    final Path dir = base.resolve(name).toAbsolutePath().normalize();
    final Optional<Project> project = detector.detect(dir).filter(supported);
    if (project.isEmpty()) {
      throw new WardException(
          "'" + name + "' is not inside a Maven, Gradle, TypeScript or JavaScript project.");
    }
    final Path root = project.get().root();
    if (!root.equals(dir)) {
      throw new WardException(
          "'"
              + name
              + "' is inside the project '"
              + display(base, root)
              + "'; use --project "
              + display(base, root)
              + ".");
    }
    return new DiscoveredProject(project.get(), 0);
  }

  /** Returns {@code root} relative to {@code base} when inside it, so messages stay short. */
  public static String display(final Path base, final Path root) {
    if (root.equals(base)) {
      return ".";
    }
    return root.startsWith(base)
        ? base.relativize(root).toString().replace('\\', '/')
        : root.toString();
  }

  private static void log(final String source, final int count) {
    LOGGER.log(Level.INFO, () -> "event=init-targets source=" + source + " count=" + count);
  }
}
