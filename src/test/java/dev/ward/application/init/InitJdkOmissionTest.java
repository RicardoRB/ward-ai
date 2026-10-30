package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.InitResult;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init on a runtime JDK that cannot run the {@code format} gate's tool. */
class InitJdkOmissionTest {
  private static final String POM = "<project><modelVersion>4.0.0</modelVersion></project>";
  private static final String REASON =
      "spotless-maven-plugin needs JDK 11+; this project builds on JDK 8";
  private static final String NOTE = "format omitted: " + REASON;

  @TempDir Path root;
  private InitService service;
  private InitSetupCatalog catalog;

  @BeforeEach
  void setUp() throws IOException {
    Files.writeString(root.resolve("pom.xml"), POM);
    catalog = JdkCatalogs.reading(new FormatOmittedOnJdk8());
    service =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(),
            catalog,
            new InitProjectFiles(
                new FormatOmittedOnJdk8(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()));
  }

  @Test
  void strictOnAJdkThatCannotRunOneGateKeepsTheOtherGatesInOrder() {
    assertThat(catalog.presetGates(service.project(root), QualityPreset.STRICT))
        .containsExactly(
            "compile",
            "tests",
            "checkstyle",
            "pmd",
            "spotbugs",
            "coverage",
            "archunit",
            "enforcer",
            "mutation");
  }

  @Test
  void dryRunNamesTheOmittedGateAndTheJdkItNeeds() {
    final InitPlan plan = service.plan(root, strict(), true);

    assertThat(plan.files())
        .filteredOn(file -> file.file().endsWith("ward.toml"))
        .singleElement()
        .extracting(PlannedFileChange::details)
        .isEqualTo(List.of(NOTE));
  }

  @Test
  void initOmitsTheGateFromWardTomlAndExplainsIt() throws IOException {
    final InitResult result = service.initialize(root, strict(), false, false, finding -> true);

    final String configuration = Files.readString(root.resolve("ward.toml"));
    assertThat(configuration).contains("[checks.checkstyle]").doesNotContain("[checks.format]");
    assertThat(result.pomWarnings()).contains(NOTE);
  }

  @Test
  void explicitlyRequestedGateWithNoVersionForTheRuntimeJdkFailsBeforeAnyWrite()
      throws IOException {
    final InitSelection selection = new InitSelection(List.of("none"), List.of("format", "pmd"));

    assertThatThrownBy(() -> service.initialize(root, selection, false, false, finding -> true))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("'format'", "JDK 8", "JDK 11+");
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(POM);
    assertThat(root.resolve("ward.toml")).doesNotExist();
  }

  private InitSelection strict() {
    return new InitSelection(
        List.of("none"),
        catalog.presetGates(service.project(root), QualityPreset.STRICT),
        "layered",
        QualityPreset.STRICT,
        List.of(),
        List.of(),
        List.of(),
        false,
        null,
        false);
  }

  /** Writes Maven tools with their default versions, and omits {@code format} as on JDK 8. */
  private static final class FormatOmittedOnJdk8 implements BuildToolConfiguration {
    private final PomToolConfigurator maven = new PomToolConfigurator();

    @Override
    public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
      return maven.apply(project, gates);
    }

    @Override
    public BuildFileChange apply(
        final Project project,
        final List<InitGateOption> gates,
        final QualityPreset preset,
        final Predicate<? super CompatibilityFinding> approval) {
      return maven.apply(project, gates, preset, approval);
    }

    @Override
    public PlannedFileChange plan(
        final Project project,
        final List<InitGateOption> gates,
        final QualityPreset preset,
        final boolean approved) {
      return maven.plan(project, gates, preset, approved);
    }

    @Override
    public List<GateOmission> omissions(final Project project, final List<String> gateIds) {
      return gateIds.contains("format") ? List.of(new GateOmission("format", REASON)) : List.of();
    }

    @Override
    public void rollback(final BuildFileChange change) {
      maven.rollback(change);
    }
  }
}
