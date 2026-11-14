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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A taint flow that crosses a file boundary is found between Kotlin files and Java files. */
class KotlinCrossFileTaintTest {

  private static final String END_METHOD = "  }\n";
  private static final String END_CLASS = "}\n";
  private static final String KOTLIN_RULE = "ward:kotlin/cross-site-scripting";
  private static final String JAVA_RULE = "ward:java/cross-site-scripting";
  private static final String KOTLIN_IMPORTS =
      "package shop\n"
          + "import javax.servlet.http.HttpServletRequest\n"
          + "import javax.servlet.http.HttpServletResponse\n";
  private static final String JAVA_IMPORTS =
      "package shop;\nimport javax.servlet.http.HttpServletRequest;\n"
          + "import javax.servlet.http.HttpServletResponse;\n";
  private static final String PAGE =
      KOTLIN_IMPORTS
          + "class Page {\n"
          + "  fun show(request: HttpServletRequest, response: HttpServletResponse) {\n"
          + "    val name = request.getParameter(\"n\")\n"
          + "    Middle().forward(name, response)\n"
          + END_METHOD
          + "  fun fromKotlin(request: HttpServletRequest, response: HttpServletResponse) {\n"
          + "    val name = Source().read(request)\n"
          + "    response.getWriter().println(name)\n"
          + END_METHOD
          + "  fun intoJava(request: HttpServletRequest, response: HttpServletResponse) {\n"
          + "    JavaSink().render(request.getParameter(\"n\"), response)\n"
          + END_METHOD
          + "  fun fromJava(request: HttpServletRequest, response: HttpServletResponse) {\n"
          + "    val name = JavaSource().read(request)\n"
          + "    response.getWriter().println(name)\n"
          + END_METHOD
          + END_CLASS;
  private static final String MIDDLE =
      KOTLIN_IMPORTS
          + "class Middle {\n"
          + "  fun forward(value: String?, response: HttpServletResponse) {\n"
          + "    response.getWriter().println(value)\n"
          + END_METHOD
          + END_CLASS;
  private static final String SOURCE =
      KOTLIN_IMPORTS
          + "class Source {\n"
          + "  fun read(request: HttpServletRequest): String? = request.getParameter(\"n\")\n"
          + END_CLASS;
  private static final String JAVA_SINK =
      JAVA_IMPORTS
          + "class JavaSink {\n"
          + "  void render(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.getWriter().println(value);\n"
          + END_METHOD
          + END_CLASS;
  private static final String JAVA_SOURCE =
      JAVA_IMPORTS
          + "class JavaSource {\n"
          + "  String read(HttpServletRequest request) {\n"
          + "    return request.getParameter(\"n\");\n"
          + END_METHOD
          + END_CLASS;
  private static final String JAVA_PAGE =
      JAVA_IMPORTS
          + "class JavaPage {\n"
          + "  void show(HttpServletRequest request, HttpServletResponse response) throws Exception {\n"
          + "    (new KotlinSink()).render(request.getParameter(\"n\"), response);\n"
          + END_METHOD
          + "  void fromKotlin(HttpServletRequest request, HttpServletResponse response)\n"
          + "      throws Exception {\n"
          + "    String name = (new Source()).read(request);\n"
          + "    response.getWriter().println(name);\n"
          + END_METHOD
          + END_CLASS;
  private static final String KOTLIN_SINK =
      KOTLIN_IMPORTS
          + "class KotlinSink {\n"
          + "  fun render(value: String?, response: HttpServletResponse) {\n"
          + "    response.getWriter().println(value)\n"
          + END_METHOD
          + END_CLASS;

  private static final int SHOW_LINE = 7;
  private static final int FROM_KOTLIN_LINE = 11;
  private static final int INTO_JAVA_LINE = 14;
  private static final int FROM_JAVA_LINE = 18;

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private List<AnalysisFinding> run(final String rule, final List<SourceUnit> units) {
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(candidate -> rule.equals(candidate.id())).toList();
    return new UnitResultCache(
            new RuleEngine(new DeclarativeRuleMatcher()),
            AnalysisCacheFixtures.codec(),
            "test",
            new JvmDependencyAdapters().forProject(root, units))
        .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
        .findings();
  }

  private static SourceUnit kotlin(final String name, final String text) {
    return new SourceUnit("src/main/kotlin/shop/" + name + ".kt", AnalysisLanguage.KOTLIN, text);
  }

  private static SourceUnit java(final String name, final String text) {
    return new SourceUnit("src/main/java/shop/" + name + ".java", AnalysisLanguage.JAVA, text);
  }

  private static List<SourceUnit> units() {
    return List.of(
        kotlin("Page", PAGE),
        kotlin("Middle", MIDDLE),
        kotlin("Source", SOURCE),
        kotlin("KotlinSink", KOTLIN_SINK),
        java("JavaSink", JAVA_SINK),
        java("JavaSource", JAVA_SOURCE),
        java("JavaPage", JAVA_PAGE));
  }

  @Test
  void aKotlinFileFollowsAValueIntoAndOutOfOtherKotlinAndJavaFiles() {
    final List<AnalysisFinding> found = run(KOTLIN_RULE, units());

    assertThat(found)
        .extracting(AnalysisFinding::file)
        .containsOnly("src/main/kotlin/shop/Page.kt");
    assertThat(found)
        .extracting(AnalysisFinding::line)
        .containsExactlyInAnyOrder(SHOW_LINE, FROM_KOTLIN_LINE, INTO_JAVA_LINE, FROM_JAVA_LINE);
  }

  @Test
  void aJavaFileFollowsAValueIntoAndOutOfAKotlinFile() {
    final List<AnalysisFinding> found = run(JAVA_RULE, units());

    assertThat(found)
        .extracting(AnalysisFinding::file)
        .containsOnly("src/main/java/shop/JavaPage.java");
    assertThat(found).hasSize(2);
  }

  @Test
  void aSafeCalleeInAnotherFileGivesNoFinding() {
    final String safeSink =
        KOTLIN_IMPORTS
            + "class Middle {\n"
            + "  fun forward(value: String?, response: HttpServletResponse) {\n"
            + "    response.getWriter().println(\"fixed\")\n"
            + END_METHOD
            + END_CLASS;
    final List<SourceUnit> safe =
        List.of(
            kotlin("Page", PAGE.replace("val name = Source().read(request)", "val name = \"x\"")),
            kotlin("Middle", safeSink),
            kotlin("Source", SOURCE),
            java("JavaSink", JAVA_SINK),
            java("JavaSource", JAVA_SOURCE));

    assertThat(run(KOTLIN_RULE, safe)).hasSize(2);
  }
}
