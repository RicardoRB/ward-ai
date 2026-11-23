package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Allow-list checks and sanitizing helpers in another class of the project (#712, #759). */
class CrossFileJavaGuardScenariosTest {
  private static final String SSRF = "ward:java/server-side-request-forgery";
  private static final String LOG = "ward:java/log-forging";
  private static final String HEAD =
      "package shop;\n"
          + "import java.util.Set;\n"
          + "import javax.servlet.http.HttpServletRequest;\n"
          + "import org.springframework.web.client.RestTemplate;\n"
          + "import org.slf4j.Logger;\n";
  private static final String CONTROLLER_HEAD =
      "class Page {\n  private final Checks checks = new Checks();\n"
          + "  Object show(HttpServletRequest request, RestTemplate rest, Logger log) {\n"
          + "    String p = request.getParameter(\"p\");\n";
  private static final String STRIP = "value.replace(\"\\r\", \"\").replace(\"\\n\", \"\")";

  private static final String THROWING =
      "  void require(String v) {\n    if (!OK.contains(v)) {\n      throw new IllegalStateException();\n"
          + "    }\n  }\n";

  private static final String RETURNING =
      "  void require(String v) {\n    if (!OK.contains(v)) {\n      return;\n    }\n  }\n";

  private static final String FETCH =
      "    return rest.getForObject(\"https://h.example/\" + p, String.class);\n";

  private static final String CONTROLLER_BODY_REQUIRE = "    checks.require(p);\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private List<AnalysisFinding> run(final String rule, final String checks, final String body) {
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(candidate -> rule.equals(candidate.id())).toList();
    final List<SourceUnit> units =
        List.of(
            new SourceUnit("shop/Checks.java", AnalysisLanguage.JAVA, HEAD + checks),
            new SourceUnit(
                "shop/Page.java",
                AnalysisLanguage.JAVA,
                HEAD + CONTROLLER_HEAD + body + "  }\n}\n"
            )
        );
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher()),
        AnalysisCacheFixtures.codec(),
        "test",
        new JvmDependencyAdapters().forProject(root, units)
    )
        .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
        .findings();
  }

  private static String checks(final String method) {
    return "class Checks {\n  static final Set<String> OK = Set.of(\"a\", \"b\");\n" + method + "}\n";
  }

  @Test
  @DisplayName("a check in a method of another class counts for the caller")
  void aCheckInAnotherClassCountsForTheCaller() {
    assertThat(run(SSRF, checks(THROWING), CONTROLLER_BODY_REQUIRE + FETCH)).isEmpty();
  }

  @Test
  @DisplayName("a check that returns instead of throwing does not count")
  void aCheckThatReturnsDoesNotCount() {
    assertThat(run(SSRF, checks(RETURNING), CONTROLLER_BODY_REQUIRE + FETCH)).hasSize(1);
  }

  @Test
  @DisplayName("no check at all is still reported")
  void noCheckIsStillReported() {
    assertThat(run(SSRF, checks(THROWING), FETCH)).hasSize(1);
  }

  @Test
  @DisplayName("a method that checks the value and returns the address is clean")
  void aMethodThatReturnsTheCheckedAddressIsClean() {
    final String method =
        "  String authorize(String v) {\n    if (!OK.contains(v)) {\n"
            + "      throw new IllegalStateException();\n    }\n"
            + "    return \"https://h.example/\" + v;\n  }\n";

    assertThat(
        run(
            SSRF,
            checks(method),
            "    return rest.getForObject(checks.authorize(p), String.class);\n"
        )
    ).isEmpty();
  }

  @Test
  @DisplayName("a method that returns the address without a check is reported")
  void aMethodWithoutACheckIsReported() {
    final String method =
        "  String authorize(String v) {\n    return \"https://h.example/\" + v;\n  }\n";

    assertThat(
        run(
            SSRF,
            checks(method),
            "    return rest.getForObject(checks.authorize(p), String.class);\n"
        )
    ).hasSize(1);
  }

  @Test
  @DisplayName("a helper of another class that strips the line breaks is a sanitizer")
  void aHelperOfAnotherClassThatStripsIsASanitizer() {
    final String helper = "  String clean(String value) {\n    return " + STRIP + ";\n  }\n";

    assertThat(
        run(LOG, checks(helper), "    log.info(checks.clean(p));\n    return p;\n")
    ).isEmpty();
  }

  @Test
  @DisplayName("a helper of another class that keeps the line breaks is reported")
  void aHelperThatKeepsTheLineBreaksIsReported() {
    final String helper = "  String clean(String value) {\n    return value.trim();\n  }\n";

    assertThat(run(LOG, checks(helper), "    log.info(checks.clean(p));\n    return p;\n"))
        .hasSize(1);
  }
}
