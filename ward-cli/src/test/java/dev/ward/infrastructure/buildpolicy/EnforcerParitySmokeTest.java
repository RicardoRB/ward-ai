package dev.ward.infrastructure.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.buildpolicy.BuildPolicyService;
import dev.ward.application.buildpolicy.BuildPolicySetting;
import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.BuildRule;
import dev.ward.domain.dependency.GraphCache;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.buildpolicy.maven.MavenBuildReader;
import dev.ward.infrastructure.dependency.maven.MavenDependencySource;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the real Maven Enforcer plugin, one rule at a time, over the Maven fixtures of the native
 * build rules and checks that both agree: Enforcer fails exactly when the native rule reports a
 * finding, and the plugin or dependency that Enforcer names is the one the finding names. It needs
 * Maven Central for {@code banDynamicVersions}, which resolves the range, and is skipped unless
 * {@code -Dward.parity.enforcer=true}: {@code ./mvnw -Dtest=EnforcerParitySmokeTest
 * -Dward.parity.enforcer=true test}.
 *
 * <p>{@code requireMavenVersion} is not compared: Enforcer checks the Maven that runs, the native
 * rule the wrapper that the project declares. {@code dependencyConvergence} and {@code
 * requireUpperBoundDeps} are compared on the fixtures under {@code build-policy/maven-inventory},
 * which need the dependency inventory and so a real Maven run; the offline fixture test skips them.
 */
@EnabledIfSystemProperty(named = "ward.parity.enforcer", matches = "true")
class EnforcerParitySmokeTest {
  private static final Path FIXTURES = Path.of("src/test/resources/build-policy/maven");
  private static final Path INVENTORY_FIXTURES =
      Path.of("src/test/resources/build-policy/maven-inventory");
  private static final Path WRAPPER = Path.of("..", "mvnw").toAbsolutePath().normalize();
  private static final String ENFORCER =
      "org.apache.maven.plugins:maven-enforcer-plugin:3.5.0:enforce";
  private static final Duration TIMEOUT = Duration.ofMinutes(3);

  private static final List<Pairing> PAIRINGS =
      List.of(
          new Pairing(
              BuildRule.PLUGIN_VERSION_PINNED, "requirePluginVersions", "maven-shade-plugin"),
          new Pairing(BuildRule.NO_DYNAMIC_VERSION, "banDynamicVersions", "junit"),
          new Pairing(
              BuildRule.DUPLICATE_DEPENDENCY_DECLARATION,
              "banDuplicatePomDependencyVersions",
              "guava"),
          new Pairing(
              BuildRule.DEPENDENCY_CONVERGENCE,
              "dependencyConvergence",
              "commons-lang3",
              INVENTORY_FIXTURES),
          new Pairing(
              BuildRule.REQUIRE_UPPER_BOUND,
              "requireUpperBoundDeps",
              "commons-lang3",
              INVENTORY_FIXTURES));

  /** One native rule and the Enforcer rule it replaces, with what each reports for a fixture. */
  private record Pairing(BuildRule rule, String enforcerRule, String named, Path fixtures) {
    Pairing(final BuildRule rule, final String enforcerRule, final String named) {
      this(rule, enforcerRule, named, FIXTURES);
    }
  }

  @Test
  void enforcerAndTheNativeRulesAgreeOnTheMavenFixtures() throws Exception {
    final List<String> disagreements = new ArrayList<>();
    for (final Pairing pairing : PAIRINGS) {
      for (final String kind : List.of("violating", "passing")) {
        final Path folder = pairing.fixtures().resolve(pairing.rule().ruleName()).resolve(kind);
        final List<Finding> findings = nativeFindings(folder, pairing.rule());
        final Run enforcer = enforcer(folder, pairing.enforcerRule());
        final boolean enforcerFails = enforcer.exitCode() != 0;
        if (enforcerFails != "violating".equals(kind)) {
          disagreements.add(folder + ": the fixture is not a " + kind + " case for Enforcer");
        } else if (enforcerFails == findings.isEmpty()) {
          disagreements.add(folder + ": enforcer fails=" + enforcerFails + ", native=" + findings);
        } else if (enforcerFails
            && !(enforcer.output().contains(pairing.named())
                && findings.stream().allMatch(f -> f.message().contains(pairing.named())))) {
          disagreements.add(folder + ": the two name different culprits");
        }
      }
    }
    assertThat(disagreements).isEmpty();
  }

  private static List<Finding> nativeFindings(final Path folder, final BuildRule rule) {
    final List<BuildModelReader> readers = List.of(new MavenBuildReader());
    final BuildPolicyService service =
        new BuildPolicyService(
            new FileSystemBuildFiles(readers),
            new DependencyInventoryService(
                Map.of(
                    BuildTool.MAVEN,
                    new MavenDependencySource(
                        new ProcessCommandExecutor(),
                        new NoGraphCache(),
                        new ForyJsonCodec(),
                        false))));
    return service.run(
        new Project(folder, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
        "policy",
        List.of(new BuildPolicySetting(rule, false, Map.of())));
  }

  private record Run(int exitCode, String output) {}

  /** Keeps nothing, so a fixture folder is never written to. */
  private static final class NoGraphCache implements GraphCache {
    @Override
    public Optional<String> load(final Path projectRoot, final String key) {
      return Optional.empty();
    }

    @Override
    public void store(final Path projectRoot, final String key, final String text) {
      // Nothing is kept.
    }
  }

  private static Run enforcer(final Path folder, final String rule)
      throws IOException, InterruptedException {
    final Path log = Files.createTempFile("enforcer", ".log");
    final Process process =
        new ProcessBuilder(
                WRAPPER.toString(),
                "-B",
                "-f",
                folder.resolve("pom.xml").toAbsolutePath().toString(),
                ENFORCER,
                "-Drules=" + rule,
                "-Denforcer.fail=true")
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("enforcer timed out on " + folder);
    }
    return new Run(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
  }
}
