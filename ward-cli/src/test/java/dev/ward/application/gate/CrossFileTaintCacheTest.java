package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A taint flow that crosses files is found, and a warm run, with files edited between runs, prints
 * what a cold run of the same files prints.
 */
class CrossFileTaintCacheTest {
  private static final String RULE = "ward:java/cross-site-scripting";
  private static final String PAGE = "src/main/java/shop/Page.java";
  private static final String MIDDLE = "src/main/java/shop/Middle.java";
  private static final String SINK = "src/main/java/shop/Sink.java";
  private static final String SOURCE = "src/main/java/shop/Source.java";
  private static final String UNRELATED = "src/main/java/shop/Unrelated.java";
  private static final String IMPORTS =
      "package shop;\nimport javax.servlet.http.HttpServletRequest;\n"
          + "import javax.servlet.http.HttpServletResponse;\n"
          + "import org.springframework.web.util.HtmlUtils;\n";
  private static final String PAGE_TEXT =
      IMPORTS
          + "class Page {\n"
          + "  void show(HttpServletRequest request, HttpServletResponse response) throws Exception {\n"
          + "    String name = request.getParameter(\"n\");\n"
          + "    (new Middle()).forward(name, response);\n"
          + "  }\n"
          + "  void fromOtherFile(HttpServletRequest request, HttpServletResponse response)\n"
          + "      throws Exception {\n"
          + "    String name = (new Source()).read(request);\n"
          + "    response.getWriter().println(name);\n"
          + "  }\n"
          + "}\n";
  private static final String MIDDLE_TEXT =
      IMPORTS
          + "class Middle {\n"
          + "  void forward(String value, HttpServletResponse response) throws Exception {\n"
          + "    Sink.render(value, response);\n"
          + "  }\n"
          + "}\n";
  private static final String SINK_UNSAFE =
      IMPORTS
          + "class Sink {\n"
          + "  static void render(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.getWriter().println(value);\n"
          + "  }\n"
          + "}\n";
  private static final String SINK_SAFE =
      IMPORTS
          + "class Sink {\n"
          + "  static void render(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.getWriter().println(HtmlUtils.htmlEscape(value));\n"
          + "  }\n"
          + "}\n";
  private static final String SOURCE_TAINTED =
      IMPORTS
          + "class Source {\n"
          + "  String read(HttpServletRequest request) {\n"
          + "    return request.getParameter(\"n\");\n"
          + "  }\n"
          + "}\n";
  private static final String SOURCE_CONSTANT =
      IMPORTS
          + "class Source {\n  String read(HttpServletRequest request) {\n    return \"x\";\n  }\n}\n";
  private static final String UNRELATED_TEXT = "package shop;\nclass Unrelated {}\n";

  @TempDir Path root;

  private final Map<String, String> entries = new HashMap<>();
  private final AnalysisCache warm = AnalysisCacheFixtures.memory(entries);
  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource()
          .load().rules().stream().filter(rule -> RULE.equals(rule.id())).toList();

  private List<AnalysisFinding> run(final AnalysisCache cache, final List<SourceUnit> units) {
    return new UnitResultCache(
            new RuleEngine(new DeclarativeRuleMatcher()),
            AnalysisCacheFixtures.codec(),
            "test",
            new JvmDependencyAdapters().forProject(root, units))
        .run(cache, units, frontends, rules)
        .findings();
  }

  private static List<SourceUnit> units(final String sink, final String source) {
    return List.of(
        unit(PAGE, PAGE_TEXT),
        unit(MIDDLE, MIDDLE_TEXT),
        unit(SINK, sink),
        unit(SOURCE, source),
        unit(UNRELATED, UNRELATED_TEXT));
  }

  private static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.JAVA, text);
  }

  private static List<String> files(final List<AnalysisFinding> findings) {
    return findings.stream().map(AnalysisFinding::file).toList();
  }

  @Test
  void aFlowThroughTwoOtherFilesAndAReturnFromAnotherFileAreFound() {
    final List<AnalysisFinding> found =
        run(AnalysisCacheFixtures.NO_CACHE, units(SINK_UNSAFE, SOURCE_TAINTED));

    assertThat(rules).hasSize(1);
    assertThat(found).hasSize(2);
    assertThat(files(found)).containsOnly(PAGE);
  }

  @Test
  void aSanitizedSinkAndAConstantSourceInOtherFilesGiveNoFinding() {
    assertThat(run(AnalysisCacheFixtures.NO_CACHE, units(SINK_SAFE, SOURCE_CONSTANT))).isEmpty();
  }

  @Test
  void aWarmRunPrintsWhatAColdRunPrintsBeforeAndAfterEditingACallee() {
    final List<SourceUnit> unsafe = units(SINK_UNSAFE, SOURCE_TAINTED);
    final List<AnalysisFinding> first = run(warm, unsafe);
    final List<AnalysisFinding> second = run(warm, unsafe);

    assertThat(first).hasSize(2);
    assertThat(second).isEqualTo(first);

    final List<SourceUnit> sinkFixed = units(SINK_SAFE, SOURCE_TAINTED);
    assertThat(run(warm, sinkFixed))
        .isEqualTo(run(AnalysisCacheFixtures.NO_CACHE, sinkFixed))
        .hasSize(1);

    final List<SourceUnit> bothFixed = units(SINK_SAFE, SOURCE_CONSTANT);
    assertThat(run(warm, bothFixed))
        .isEqualTo(run(AnalysisCacheFixtures.NO_CACHE, bothFixed))
        .isEmpty();

    assertThat(run(warm, unsafe)).isEqualTo(first);
  }
}
