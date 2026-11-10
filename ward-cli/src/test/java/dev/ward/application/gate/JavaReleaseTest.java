package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The detected Java release flows into the {@code java-release} option of the rules that read it.
 */
class JavaReleaseTest {

  private static final String ACCESSOR = "ward:java/accessor-class-generation";
  private static final String PLAIN = "ward:java/type-name";
  private static final int EIGHT = 8;
  private static final int SEVENTEEN = 17;

  @Test
  void aVersionIsReadAsItsReleaseAndAnUnknownOneAsUnknown() {
    assertThat(JavaRelease.of(project("8"))).isEqualTo(EIGHT);
    assertThat(JavaRelease.of(project("1.8"))).isEqualTo(EIGHT);
    assertThat(JavaRelease.of(project("17"))).isEqualTo(SEVENTEEN);
    assertThat(JavaRelease.of(project(""))).isEqualTo(JavaRelease.UNKNOWN);
    assertThat(JavaRelease.of(project("0"))).isEqualTo(JavaRelease.UNKNOWN);
    assertThat(JavaRelease.of(project("latest"))).isEqualTo(JavaRelease.UNKNOWN);
  }

  @Test
  void theDetectedReleaseIsTheDefaultOfTheOptionOnly() {
    final RuleDefinition accessor = rule(ACCESSOR);
    final RuleDefinition plain = rule(PLAIN);

    assertThat(JavaRelease.withDefault(accessor, EIGHT).body()).contains("java-release = " + EIGHT);
    assertThat(JavaRelease.withDefault(plain, EIGHT)).isSameAs(plain);
  }

  @Test
  void aSelectedRuleGetsTheDetectedReleaseWithoutAConfigurationFile() {
    final ConfiguredRuleSetup setup =
        new ConfiguredRuleSetup(
            "ward",
            null,
            new ConfiguredRuleSetup.Choices(
                List.of(rule(ACCESSOR)), false, List.of(rule(ACCESSOR)), List.of()),
            new ClasspathRuleSource());

    final List<RuleDefinition> rules = setup.resolve(Path.of("."), EIGHT).rules();

    assertThat(rules).hasSize(1);
    assertThat(rules.get(0).body()).contains("java-release = " + EIGHT);
  }

  private static Project project(final String version) {
    return new Project(
        Path.of("."),
        Language.JAVA,
        BuildTool.MAVEN,
        Framework.NONE,
        false,
        false,
        Path.of("."),
        Set.of(),
        version,
        null,
        "");
  }

  private static RuleDefinition rule(final String id) {
    final Optional<RuleDefinition> found =
        new ClasspathRuleSource()
            .load().rules().stream().filter(rule -> rule.id().equals(id)).findFirst();
    return found.orElseThrow();
  }
}
