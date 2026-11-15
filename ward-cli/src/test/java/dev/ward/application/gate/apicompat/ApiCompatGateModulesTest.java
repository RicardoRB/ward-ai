package dev.ward.application.gate.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiCompatGateModulesTest {
  private static final String ROOT_POM =
      "<project><modules><module>core</module><module>web</module></modules></project>";
  private static final String POM = "<project></project>";
  private static final String CORE = "core/src/main/java/p/Client.java";
  private static final String WEB = "web/src/main/java/w/Page.java";
  private static final String CLIENT =
      "package p;\npublic class Client {\n  public void close() {}\n  public void open() {}\n}\n";
  private static final String CLIENT_WITHOUT_CLOSE =
      "package p;\npublic class Client {\n  public void open() {}\n}\n";
  private static final String PAGE =
      "package w;\npublic class Page {\n  public void show() {}\n}\n";
  private static final String PAGE_WITHOUT_SHOW = "package w;\npublic class Page {\n}\n";

  private final ApiCompatGateFixtures fixtures = new ApiCompatGateFixtures();

  private void both(final String path, final String text) {
    fixtures.baseline(path, text);
    fixtures.working.put(path, text);
  }

  private void mavenBuild() {
    both("pom.xml", ROOT_POM);
    both("core/pom.xml", POM);
    both("web/pom.xml", POM);
  }

  @Test
  void removedMethodInAModuleFailsAndNamesTheModule() {
    mavenBuild();
    both(CORE, CLIENT);
    fixtures.baseline(WEB, PAGE);
    fixtures.working.put(WEB, PAGE_WITHOUT_SHOW);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items()).hasSize(1);
    assertThat(result.findings().items().getFirst().message()).startsWith("[web] w.Page#show");
    assertThat(result.findings().items().getFirst().file()).isEqualTo(WEB);
  }

  @Test
  void unchangedModulesPassAndCountTheTypesOfAll() {
    mavenBuild();
    both(CORE, CLIENT);
    both(WEB, PAGE);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("2 API types");
  }

  @Test
  void gradleIncludeFindsTheProjects() {
    both("settings.gradle.kts", "rootProject.name = \"x\"\ninclude(\":core\", \":web\")\n");
    both("core/build.gradle.kts", "plugins { java }");
    fixtures.baseline(CORE, CLIENT);
    fixtures.working.put(CORE, CLIENT_WITHOUT_CLOSE);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items().getFirst().message()).startsWith("[core] p.Client#close");
  }

  @Test
  void newModuleHasNoBaselineAndIsNotAnError() {
    both(
        "pom.xml",
        "<project><modules><module>core</module><module>web</module></modules></project>");
    both("core/pom.xml", POM);
    both(CORE, CLIENT);
    fixtures.working.put("web/pom.xml", POM);
    fixtures.working.put(WEB, PAGE);

    assertThat(fixtures.run().status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void removedModuleReportsItsTypesRemoved() {
    both("core/pom.xml", POM);
    both(CORE, CLIENT);
    fixtures.baseline("pom.xml", ROOT_POM);
    fixtures.baseline("web/pom.xml", POM);
    fixtures.baseline(WEB, PAGE);
    fixtures.working.put("pom.xml", "<project><modules><module>core</module></modules></project>");

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items().getFirst().ruleId()).isEqualTo("api-compat:type-removed");
    assertThat(result.findings().items().getFirst().message()).startsWith("[web] w.Page");
  }

  @Test
  void typeMovedToAnotherModuleIsReported() {
    mavenBuild();
    fixtures.baseline(CORE, CLIENT);
    fixtures.working.put("web/src/main/java/p/Client.java", CLIENT);

    final GateResult result = fixtures.run();

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items().getFirst().ruleId())
        .isEqualTo("api-compat:type-module-changed");
    assertThat(result.findings().items().getFirst().message())
        .contains("p.Client: module core -> module web");
  }

  @Test
  void modulesOptionLimitsTheComparedApi() {
    mavenBuild();
    both(CORE, CLIENT);
    fixtures.baseline(WEB, PAGE);
    fixtures.working.put(WEB, PAGE_WITHOUT_SHOW);

    final GateResult result = fixtures.run(new ApiCompatOptions(null, List.of(), List.of("core")));

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("1 API types");
  }

  @Test
  void unknownModuleIsAnExecutionError() {
    mavenBuild();
    both(CORE, CLIENT);

    final GateResult result = fixtures.run(new ApiCompatOptions(null, List.of(), List.of("cor")));

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.summary()).contains("'cor'");
  }
}
