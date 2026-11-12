package dev.ward.application.gate.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.jvm.types.JavaApiSurfaceReader;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.MainSourceRoots;
import dev.ward.domain.gate.WorkingFiles;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.jvm.JvmSourceLayouts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ApiCompatGateTest {
  private static final Path ROOT = Path.of("/project");
  private static final Project PROJECT =
      new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);
  private static final String MAIN = "src/main/java/p/";
  private static final String BASE_COMMIT = "commit-v1.0.0";
  private static final String CLIENT =
      "package p;\npublic class Client {\n  public void close() {}\n  public void open() {}\n}\n";
  private static final String CLIENT_WITHOUT_CLOSE =
      "package p;\npublic class Client {\n  public void open() {}\n}\n";

  private final FakeRevisions git = new FakeRevisions().ref("HEAD", "head").tag("v1.0.0");
  private final Map<String, String> working = new TreeMap<>();

  private void baseline(final String path, final String text) {
    git.file(BASE_COMMIT, path, text);
  }

  private GateResult run(final ApiCompatOptions options) {
    final WorkingFiles files =
        new WorkingFiles() {
          @Override
          public List<String> files(final Path root, final String directory) {
            final String prefix = directory + "/";
            return working.keySet().stream()
                .filter(path -> path.startsWith(prefix))
                .map(path -> path.substring(prefix.length()))
                .toList();
          }

          @Override
          public Optional<String> read(final Path root, final String path) {
            return Optional.ofNullable(working.get(path));
          }
        };
    final MainSourceRoots layout = JvmSourceLayouts::mainRoots;
    final ApiSurfaces surfaces =
        (root, units) ->
            new JavaApiSurfaceReader()
                .read(
                    units,
                    new AntlrLanguageFrontend(),
                    name ->
                        ClassLoader.getSystemResource(name.replace('.', '/') + ".class") != null);
    return new ApiCompatGate(
            "api-compat", options, new ApiCompatPorts(git, files, layout, surfaces))
        .execute(PROJECT);
  }

  private GateResult run() {
    return run(ApiCompatOptions.DEFAULTS);
  }

  @Test
  void unchangedApiPassesAndNamesBaselineAndCount() {
    baseline(MAIN + "Client.java", CLIENT);
    working.put(MAIN + "Client.java", CLIENT);

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("v1.0.0").contains("1 API types");
    assertThat(result.findings().items()).isEmpty();
  }

  @Test
  void removedMethodFailsWithAFindingAtTheEnclosingType() {
    baseline(MAIN + "Client.java", CLIENT);
    working.put(MAIN + "Client.java", CLIENT_WITHOUT_CLOSE);

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo("api-compat:member-removed");
    assertThat(finding.message()).contains("p.Client#close").endsWith("removed");
    assertThat(finding.file()).isEqualTo(MAIN + "Client.java");
    assertThat(finding.line()).isEqualTo(2);
  }

  @Test
  void uncommittedNewSourcesAreTheNewApi() {
    baseline(MAIN + "Client.java", CLIENT);
    working.put(MAIN + "Client.java", CLIENT);
    working.put(MAIN + "Added.java", "package p;\npublic class Added {}\n");

    assertThat(run().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void testSourcesAreNotApi() {
    baseline(MAIN + "Client.java", CLIENT);
    baseline("src/test/java/p/Helper.java", "package p;\npublic class Helper {}\n");
    working.put(MAIN + "Client.java", CLIENT);

    assertThat(run().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void includeLimitsTheApi() {
    baseline(MAIN + "Client.java", CLIENT);
    baseline("src/main/java/q/Internal.java", "package q;\npublic class Internal {}\n");
    working.put(MAIN + "Client.java", CLIENT);

    assertThat(run(new ApiCompatOptions(null, List.of("p"))).status()).isEqualTo(GateStatus.PASSED);
    assertThat(run().status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void includeThatMatchesNothingIsAnExecutionError() {
    baseline(MAIN + "Client.java", CLIENT);
    working.put(MAIN + "Client.java", CLIENT);

    final GateResult result = run(new ApiCompatOptions(null, List.of("p.apii")));

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("p.apii");
  }

  @Test
  void baselineWithoutSourcesIsAnExecutionError() {
    working.put(MAIN + "Client.java", CLIENT);

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("no sources").contains("v1.0.0");
  }

  @Test
  void unparseableBaselineFileNamesFileAndRef() {
    baseline(MAIN + "Client.java", "package p;\npublic class Client { void m( }\n");
    working.put(MAIN + "Client.java", CLIENT);

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(MAIN + "Client.java").contains("v1.0.0");
  }

  @Test
  void unparseableWorkingFileNamesTheFile() {
    baseline(MAIN + "Client.java", CLIENT);
    working.put(MAIN + "Client.java", "package p;\npublic class Client { void m( }\n");

    final GateResult result = run();

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains(MAIN + "Client.java").contains("working tree");
  }

  @Test
  void missingBaselineIsAnExecutionErrorNamingTheCause() {
    final FakeRevisions noTag = new FakeRevisions().ref("HEAD", "head");
    final GateResult result =
        new ApiCompatGate(
                "api-compat",
                ApiCompatOptions.DEFAULTS,
                new ApiCompatPorts(
                    noTag,
                    new WorkingFiles() {
                      @Override
                      public List<String> files(final Path root, final String directory) {
                        return List.of();
                      }

                      @Override
                      public Optional<String> read(final Path root, final String path) {
                        return Optional.empty();
                      }
                    },
                    JvmSourceLayouts::mainRoots,
                    (root, units) -> List.of()))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("no release tag").contains("'base'");
  }

  @Test
  void customSourceDirectoryAtTheBaselineIsRead() {
    baseline("pom.xml", "<project><build><sourceDirectory>lib</sourceDirectory></build></project>");
    baseline("lib/p/Client.java", CLIENT);
    working.put(
        "pom.xml", "<project><build><sourceDirectory>lib</sourceDirectory></build></project>");
    working.put("lib/p/Client.java", CLIENT_WITHOUT_CLOSE);

    assertThat(run().status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void logsCountsAndKindsButNeverSourceText() {
    final String secret = "SecretIdentifierXyz";
    baseline(MAIN + "Client.java", CLIENT.replace("close", secret));
    working.put(MAIN + "Client.java", CLIENT_WITHOUT_CLOSE);
    final List<String> lines = new ArrayList<>();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord record) {
            final Object[] parameters = record.getParameters();
            lines.add(
                record.getMessage()
                    + (parameters == null ? "" : java.util.Arrays.toString(parameters)));
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    final Logger logger = Logger.getLogger("dev.ward.application.gate.apicompat");
    logger.addHandler(handler);
    final Level previous = logger.getLevel();
    logger.setLevel(Level.ALL);
    try {
      run();
    } finally {
      logger.removeHandler(handler);
      logger.setLevel(previous);
    }

    assertThat(String.join("\n", lines))
        .contains("event=api-compat-baseline")
        .contains("event=api-compat-findings")
        .contains("member-removed")
        .doesNotContain(secret);
  }
}
