package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildModulePlanning;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.inject.Inject;

/** Routes JVM initialization mutations to the Maven or Gradle adapter. */
public final class JvmBuildToolConfigurator implements BuildToolConfiguration, BuildModulePlanning {
  private final PomToolConfigurator maven;
  private final GradleToolConfigurator gradle;

  /** Creates the configurator that delegates to the Maven or Gradle configurator. */
  @Inject
  public JvmBuildToolConfigurator(
      final PomToolConfigurator maven, final GradleToolConfigurator gradle) {
    this.maven = maven;
    this.gradle = gradle;
  }

  @Override
  public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
    return adapter(project).apply(project, gates);
  }

  @Override
  public BuildFileChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    return adapter(project).apply(project, gates, preset, approval);
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    return adapter(project).plan(project, gates, preset, approved);
  }

  /** Plans the module build files of a Gradle or Maven build; other builds have none. */
  @Override
  public List<PlannedFileChange> planModules(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved) {
    return switch (project.buildTool()) {
      case GRADLE -> gradle.planModules(project, gates, preset, approved);
      case MAVEN -> maven.planModules(project, gates, preset, approved);
      default -> List.of();
    };
  }

  @Override
  public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    return adapter(project).omissions(project, gateIds);
  }

  @Override
  public List<GateOmission> unsupportedPins(final Project project, final List<String> gateIds) {
    return adapter(project).unsupportedPins(project, gateIds);
  }

  @Override
  public Optional<Integer> runtimeJdk(final Project project) {
    return adapter(project).runtimeJdk(project);
  }

  @Override
  public void rollback(final BuildFileChange change) {
    if (change == null) {
      return;
    }
    final Path file = change.file();
    if (file == null) {
      return;
    }
    final String fileName = file.toString();
    if (fileName.endsWith("/build.gradle") || fileName.endsWith("/build.gradle.kts")) {
      gradle.rollback(change);
    } else {
      maven.rollback(change);
    }
  }

  private BuildToolConfiguration adapter(final Project project) {
    return project.buildTool() == BuildTool.GRADLE ? gradle : maven;
  }
}
