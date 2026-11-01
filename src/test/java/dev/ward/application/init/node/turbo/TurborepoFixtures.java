package dev.ward.application.init.node.turbo;

import dev.ward.application.init.InitInputs;
import dev.ward.application.init.SwiftPorts;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds a Turborepo workspace of members under {@code root} for the planning tests. */
final class TurborepoFixtures {
  static final String WEB = "apps/web";
  static final String API = "apps/api";
  static final PackageManifest EMPTY = PackageManifest.EMPTY;

  private TurborepoFixtures() {}

  static Project rootProject(final Path root, final BuildTool manager) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        manager,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.TURBO,
        null);
  }

  static WorkspaceMember memberOf(
      final Path root, final String relative, final Framework framework) {
    final Path dir = root.resolve(relative);
    return new WorkspaceMember(
        relative,
        dir,
        new Project(
            dir,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            framework,
            false,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE,
            null));
  }

  /**
   * The inputs of a root whose members all hold sources, unless {@code withSources} says otherwise.
   */
  static InitInputs inputs(
      final Path root,
      final List<WorkspaceMember> members,
      final Map<Path, PackageManifest> manifests,
      final Set<Path> withSources) {
    return new InitInputs(
        new FakeEnvironmentInspection("npm", "pnpm", "turbo"),
        dir -> manifests.getOrDefault(dir, EMPTY),
        withSources::contains,
        (dir, gate) -> Optional.empty(),
        Optional.empty(),
        (dir, tool) -> Optional.empty(),
        new Members(members),
        SwiftPorts.NONE,
        dev.ward.domain.init.BuildModulePlanning.none());
  }

  static InitInputs inputs(
      final Path root,
      final List<WorkspaceMember> members,
      final Map<Path, PackageManifest> manifests) {
    return inputs(
        root,
        members,
        manifests,
        members.stream().map(WorkspaceMember::directory).collect(Collectors.toSet()));
  }

  /** Lists the given members for the workspace root. */
  private record Members(List<WorkspaceMember> members) implements WorkspaceMembers {
    @Override
    public List<WorkspaceMember> members(final Path ignored) {
      return members;
    }

    @Override
    public Optional<String> packageOf(final Path ignored, final Path path) {
      return Optional.empty();
    }
  }
}
