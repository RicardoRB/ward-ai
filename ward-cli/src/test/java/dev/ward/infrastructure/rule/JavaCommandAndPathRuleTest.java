package dev.ward.infrastructure.rule;

import static dev.ward.infrastructure.rule.JavaSecurityRuleRun.IMPORTS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Scenarios of the command and path rules, run through the rule engine with full paths. */
final class JavaCommandAndPathRuleTest {

  private static final String COMMAND = "ward:java/command-injection";
  private static final String PATH = "ward:java/path-injection";

  /** Scenario: command injection through Runtime.exec on a getRuntime() call. */
  @Test
  void runtimeExecReachesTheCommand() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            COMMAND,
            IMPORTS
                + """
                class Run {
                  void run(HttpServletRequest request) throws Exception {
                    String cmd = request.getParameter("cmd");
                    Runtime.getRuntime().exec(cmd);
                  }
                }
                """);
    assertThat(found).hasSize(1);
    assertThat(JavaTaintFixtures.messages(found.get(0).path()))
        .last()
        .isEqualTo("sink: exec argument 0");
  }

  /** Scenario: command injection through a ProcessBuilder constructor, in any of its arguments. */
  @Test
  void processBuilderConstructorReachesTheCommand() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            COMMAND,
            IMPORTS
                + """
                class Run {
                  void run(HttpServletRequest request) throws Exception {
                    String cmd = request.getParameter("cmd");
                    new ProcessBuilder("sh", "-c", cmd).start();
                  }
                }
                """);
    assertThat(found).hasSize(1);
  }

  /** Scenario: path injection from a Spring request parameter through a File constructor. */
  @Test
  void springRequestParamReachesTheFileConstructor() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            PATH,
            """
            import java.io.File;
            import org.springframework.web.bind.annotation.RequestParam;
            class Files {
              void run(@RequestParam String name) {
                File file = new File("/data", name);
              }
            }
            """);
    assertThat(found).hasSize(1);
    assertThat(JavaTaintFixtures.messages(found.get(0).path()))
        .containsExactly(
            "source: org.springframework.web.bind.annotation.RequestParam",
            "sink: <init> argument 1");
  }

  /** Every bundled Java specification file loads, so no entry is malformed. */
  @Test
  void everyBundledJavaSpecLoads() {
    final TaintSpec spec =
        TaintSpecFiles.load(
            List.of(
                "taint/java/sources.toml",
                "taint/java/sql.toml",
                "taint/java/command.toml",
                "taint/java/path.toml"));
    assertThat(spec.sources()).isNotEmpty();
    assertThat(spec.sinks()).isNotEmpty();
    assertThat(spec.sanitizers()).isNotEmpty();
  }
}
