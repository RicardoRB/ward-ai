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
 * A call through an interface, a variable, a field or a parameter reaches the method of the class
 * the receiver was created as or is declared as; a receiver whose class is not decided is not
 * followed.
 */
class VirtualDispatchTaintTest {
  private static final String END = "  }\n";
  private static final String RULE = "ward:java/cross-site-scripting";
  private static final String DIR = "src/main/java/shop/";
  private static final String ROUTER = DIR + "Router.java";
  private static final String HEADER =
      "package shop;\nimport javax.servlet.http.HttpServletRequest;\n"
          + "import javax.servlet.http.HttpServletResponse;\n"
          + "import org.springframework.web.util.HtmlUtils;\n";
  private static final String HANDLER =
      HEADER
          + "interface Handler {\n"
          + "  void handle(String v, HttpServletResponse r) throws Exception;\n"
          + "}\n";
  private static final String UNSAFE =
      HEADER
          + "class Unsafe implements Handler {\n"
          + "  public void handle(String v, HttpServletResponse r) throws Exception {\n"
          + "    r.getWriter().println(v);\n"
          + END
          + "}\n";
  private static final String SAFE =
      HEADER
          + "class Safe implements Handler {\n"
          + "  public void handle(String v, HttpServletResponse r) throws Exception {\n"
          + "    r.getWriter().println(HtmlUtils.htmlEscape(v));\n"
          + END
          + "}\n";
  private static final String ROUTER_TEXT =
      HEADER
          + "class Router {\n"
          + "  private final Handler fixed = new Unsafe();\n"
          + "  private Handler held;\n"
          + "  Router(Handler h) { held = h; }\n"
          + "  void viaCreation(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    Handler h = new Unsafe();\n"
          + "    h.handle(q.getParameter(\"n\"), r);\n"
          + END
          + "  void viaSafeCreation(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    Handler s = new Safe();\n"
          + "    s.handle(q.getParameter(\"n\"), r);\n"
          + END
          + "  void viaFinalField(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    fixed.handle(q.getParameter(\"n\"), r);\n"
          + END
          + "  void viaDeclaredClass(Unsafe u, HttpServletRequest q, HttpServletResponse r)\n"
          + "      throws Exception {\n"
          + "    u.handle(q.getParameter(\"n\"), r);\n"
          + END
          + "  void viaInterfaceField(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    held.handle(q.getParameter(\"n\"), r);\n"
          + END
          + "  void viaTwoCreations(boolean c, HttpServletRequest q, HttpServletResponse r)\n"
          + "      throws Exception {\n"
          + "    Handler t;\n"
          + "    if (c) { t = new Unsafe(); } else { t = new Safe(); }\n"
          + "    t.handle(q.getParameter(\"n\"), r);\n"
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
  void callsThroughCreatedAndDeclaredClassesAreFollowedAndUndecidedOnesAreNot() {
    final List<SourceUnit> units =
        List.of(
            unit("Handler", HANDLER),
            unit("Unsafe", UNSAFE),
            unit("Safe", SAFE),
            unit("Router", ROUTER_TEXT));
    final List<AnalysisFinding> found =
        new UnitResultCache(
                new RuleEngine(new DeclarativeRuleMatcher()),
                AnalysisCacheFixtures.codec(),
                "test",
                new JvmDependencyAdapters().forProject(root, units))
            .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
            .findings();

    assertThat(rules).hasSize(1);
    assertThat(found).extracting(AnalysisFinding::file).containsOnly(ROUTER);
    assertThat(found)
        .extracting(AnalysisFinding::line)
        .containsExactlyInAnyOrder(lineOf("h.handle"), lineOf("fixed.handle"), lineOf("u.handle"));
  }

  private static int lineOf(final String call) {
    final String[] lines = ROUTER_TEXT.split("\n", -1);
    for (int index = 0; index < lines.length; index++) {
      if (lines[index].contains(call)) {
        return index + 1;
      }
    }
    throw new IllegalStateException(call);
  }
}
