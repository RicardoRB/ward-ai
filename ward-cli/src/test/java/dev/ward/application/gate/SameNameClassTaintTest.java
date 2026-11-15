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
 * Two classes with one simple name, method name and arity in different packages are told apart by
 * their package: the page that imports the unsafe one is reported, the page that imports the safe
 * one is not.
 */
class SameNameClassTaintTest {
  private static final int CALLS = 3;
  private static final String RULE = "ward:java/cross-site-scripting";
  private static final String UNSAFE_PAGE = "src/main/java/shop/UnsafePage.java";
  private static final String IMPORTS =
      "import javax.servlet.http.HttpServletRequest;\n"
          + "import javax.servlet.http.HttpServletResponse;\n"
          + "import org.springframework.web.util.HtmlUtils;\n";
  private static final String UTIL_BODY =
      "public class Util {\n"
          + "  public static void render(String v, HttpServletResponse r) throws Exception {\n"
          + "    r.getWriter().println(%1$s);\n"
          + "  }\n"
          + "  public void show(String v, HttpServletResponse r) throws Exception {\n"
          + "    r.getWriter().println(%1$s);\n"
          + "  }\n"
          + "}\n";
  private static final String PAGE_BODY =
      "public class %1$s {\n"
          + "  void viaStatic(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    Util.render(q.getParameter(\"n\"), r);\n"
          + "  }\n"
          + "  void viaCreation(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    (new Util()).show(q.getParameter(\"n\"), r);\n"
          + "  }\n"
          + "  void viaVariable(HttpServletRequest q, HttpServletResponse r) throws Exception {\n"
          + "    Util u = new Util();\n"
          + "    u.show(q.getParameter(\"n\"), r);\n"
          + "  }\n"
          + "}\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource()
          .load().rules().stream().filter(rule -> RULE.equals(rule.id())).toList();

  private static SourceUnit unit(final String path, final String text) {
    return new SourceUnit("src/main/java/" + path, AnalysisLanguage.JAVA, text);
  }

  private static SourceUnit util(final String pack, final String value) {
    return unit(
        pack + "/Util.java", "package " + pack + ";\n" + IMPORTS + UTIL_BODY.formatted(value));
  }

  private static SourceUnit page(final String name, final String imported) {
    return unit(
        "shop/" + name + ".java",
        "package shop;\nimport " + imported + ".Util;\n" + IMPORTS + PAGE_BODY.formatted(name));
  }

  @Test
  void theClassOfTheImportedPackageDecidesWhichMethodACallReaches() {
    final List<SourceUnit> units =
        List.of(
            util("unsafe", "v"),
            util("safe", "HtmlUtils.htmlEscape(v)"),
            page("UnsafePage", "unsafe"),
            page("SafePage", "safe"));
    final List<AnalysisFinding> found =
        new UnitResultCache(
                new RuleEngine(new DeclarativeRuleMatcher()),
                AnalysisCacheFixtures.codec(),
                "test",
                new JvmDependencyAdapters().forProject(root, units))
            .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
            .findings();

    assertThat(rules).hasSize(1);
    assertThat(found).extracting(AnalysisFinding::file).containsOnly(UNSAFE_PAGE).hasSize(CALLS);
  }
}
