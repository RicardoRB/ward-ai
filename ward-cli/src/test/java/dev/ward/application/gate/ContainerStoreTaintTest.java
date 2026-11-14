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

/**
 * A container that receives a tainted value is tainted: an array element, a field of a holder
 * object, and a collection or builder that a propagator stores into. A container that only gets
 * constants is clean.
 */
class ContainerStoreTaintTest {
  private static final String END = "  }\n";
  private static final String RULE = "ward:java/cross-site-scripting";
  private static final String DIR = "src/main/java/shop/";
  private static final String PAGE = DIR + "Page.java";
  private static final String HEADER =
      "package shop;\nimport java.util.ArrayList;\nimport java.util.List;\n"
          + "import javax.servlet.http.HttpServletRequest;\n"
          + "import javax.servlet.http.HttpServletResponse;\n";
  private static final String BOX = HEADER + "class Box {\n  String value;\n}\n";
  private static final String SINK =
      HEADER
          + "class Sink {\n"
          + "  void fromArray(String[] a, HttpServletResponse r) throws Exception {\n"
          + "    String d = a[0];\n"
          + "    r.getWriter().println(d);\n"
          + END
          + "  void fromBox(Box b, HttpServletResponse r) throws Exception {\n"
          + "    r.getWriter().println(b.value);\n"
          + END
          + "  void fromList(List<String> l, HttpServletResponse r) throws Exception {\n"
          + "    r.getWriter().println(l.get(0));\n"
          + END
          + "}\n";
  private static final String PAGE_TEXT =
      HEADER
          + "class Page {\n"
          + "  void arrayStore(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    String[] a = new String[2];\n"
          + "    a[0] = q.getParameter(\"n\");\n"
          + "    (new Sink()).fromArray(a, r);\n"
          + END
          + "  void holderStore(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    Box b = new Box();\n"
          + "    b.value = q.getParameter(\"n\");\n"
          + "    (new Sink()).fromBox(b, r);\n"
          + END
          + "  void listStore(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    List<String> l = new ArrayList<>();\n"
          + "    l.add(q.getParameter(\"n\"));\n"
          + "    (new Sink()).fromList(l, r);\n"
          + END
          + "  void constantStore(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    String[] a = new String[2];\n"
          + "    a[0] = \"x\";\n"
          + "    (new Sink()).fromArray(a, r);\n"
          + END
          + "  void builderStore(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    StringBuilder sb = new StringBuilder();\n"
          + "    sb.append(q.getParameter(\"n\"));\n"
          + "    r.getWriter().println(sb.toString());\n"
          + END
          + "}\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource()
          .load().rules().stream().filter(rule -> RULE.equals(rule.id())).toList();

  private static SourceUnit unit(final String name, final String text) {
    return new SourceUnit(DIR + name + ".java", AnalysisLanguage.JAVA, text);
  }

  @Test
  void aContainerThatReceivedTaintIsTaintedAndAConstantOneIsNot() {
    final List<SourceUnit> units =
        List.of(unit("Box", BOX), unit("Sink", SINK), unit("Page", PAGE_TEXT));
    final List<AnalysisFinding> found =
        new UnitResultCache(
                new RuleEngine(new DeclarativeRuleMatcher()),
                AnalysisCacheFixtures.codec(),
                "test",
                new JvmDependencyAdapters().forProject(root, units))
            .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
            .findings();

    assertThat(rules).hasSize(1);
    assertThat(found).extracting(AnalysisFinding::file).containsOnly(PAGE);
    assertThat(found)
        .extracting(AnalysisFinding::line)
        .containsExactlyInAnyOrder(
            lineOf("fromArray(a, r);"),
            lineOf("fromBox(b, r)"),
            lineOf("fromList(l, r)"),
            lineOf("println(sb.toString())"));
  }

  private static int lineOf(final String text) {
    final String[] lines = PAGE_TEXT.split("\n", -1);
    for (int index = 0; index < lines.length; index++) {
      if (lines[index].contains(text)) {
        return index + 1;
      }
    }
    throw new IllegalStateException(text);
  }
}
