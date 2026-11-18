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

/** The scenarios of story #603: Java taint across files through dispatch, fields and collections. */
class CrossFileJavaTaintScenariosTest {
  private static final String SHOP = "shop";
  private static final int SANITISED_FROM_LINE = 12;
  private static final int SINK_FLOWS = 3;
  private static final int UNSAFE_CALL_LINE = 8;
  private static final String RULE = "ward:java/cross-site-scripting";
  private static final String IMPORTS =
      "import javax.servlet.http.HttpServletRequest;\n"
          + "import javax.servlet.http.HttpServletResponse;\n"
          + "import org.springframework.web.util.HtmlUtils;\n";

  private static final String PAGE_HEAD =
      "class Page {\n  void show(HttpServletRequest request, HttpServletResponse response,"
          + " Renderer renderer) throws Exception {\n    String name = request.getParameter(\"n\");\n";

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource()
          .load().rules().stream().filter(rule -> RULE.equals(rule.id())).toList();

  private List<AnalysisFinding> run(final String... pathsAndTexts) {
    final List<SourceUnit> units =
        java.util.stream.IntStream.range(0, pathsAndTexts.length / 2)
            .mapToObj(
                i ->
                    new SourceUnit(
                        pathsAndTexts[2 * i],
                        AnalysisLanguage.JAVA,
                        pathsAndTexts[2 * i + 1]
                    )
            )
            .toList();
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher()),
        AnalysisCacheFixtures.codec(),
        "test",
        new JvmDependencyAdapters().forProject(root, units)
    )
        .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
        .findings();
  }

  private static String file(final String pkg, final String body) {
    return "package " + pkg + ";\n" + IMPORTS + body;
  }

  @Test
  @DisplayName("a flow through an interface")
  void aFlowThroughAnInterface() {
    // Given a tainted value passed to a method called through an interface, whose implementation
    // in another file reaches a sink
    final String renderer = file(
        SHOP,
        "interface Renderer { void render(String v, "
            + "HttpServletResponse r) throws Exception; }\n"
    );
    final String impl = file(
        SHOP,
        "class HtmlRenderer implements Renderer {\n"
            + "  public void render(String v, HttpServletResponse r) throws Exception {\n"
            + "    r.getWriter().println(v);\n  }\n}\n"
    );
    final String page = file(SHOP, PAGE_HEAD + "    renderer.render(name, response);\n  }\n}\n");
    // When the Java taint rules run
    final List<AnalysisFinding> found =
        run(
            SHOP + "/Renderer.java",
            renderer,
            SHOP + "/HtmlRenderer.java",
            impl,
            SHOP + "/Page.java",
            page
        );
    // Then the sink is reported
    assertThat(found).hasSize(1);
    assertThat(found.get(0).file()).isEqualTo(SHOP + "/Page.java");
  }

  @Test
  void aCallThroughAnInterfaceWithTwoImplementorsIsNotFollowed() {
    final String renderer = file(
        SHOP,
        "interface Renderer { void render(String v, "
            + "HttpServletResponse r) throws Exception; }\n"
    );
    final String unsafe = file(
        SHOP,
        "class A implements Renderer {\n"
            + "  public void render(String v, HttpServletResponse r) throws Exception {\n"
            + "    r.getWriter().println(v);\n  }\n}\n"
    );
    final String safe = file(
        SHOP,
        "class B implements Renderer {\n"
            + "  public void render(String v, HttpServletResponse r) {}\n}\n"
    );
    final String page = file(SHOP, PAGE_HEAD + "    renderer.render(name, response);\n  }\n}\n");
    assertThat(
        run(
            SHOP + "/Renderer.java",
            renderer,
            SHOP + "/A.java",
            unsafe,
            SHOP + "/B.java",
            safe,
            SHOP + "/Page.java",
            page
        )
    ).isEmpty();
  }

  @Test
  @DisplayName("a flow through a field or a collection")
  void aFlowThroughAFieldOrACollection() {
    // Given a tainted value stored in a field, an array or a collection in one class and read
    // into a sink in another
    final String printer = file(
        SHOP,
        "class Printer {\n"
            + "  static void field(Box b, HttpServletResponse r) throws Exception {"
            + " r.getWriter().println(b.value); }\n"
            + "  static void array(String[] a, HttpServletResponse r) throws Exception {"
            + " r.getWriter().println(a[0]); }\n"
            + "  static void list(java.util.List<String> l, HttpServletResponse r) throws Exception {"
            + " r.getWriter().println(l.get(0)); }\n}\n"
    );
    final String box = file(SHOP, "class Box { String value; }\n");
    final String head = "class Page {\n  void show(HttpServletRequest request,"
        + " HttpServletResponse response) throws Exception {\n"
        + "    String name = request.getParameter(\"n\");\n";
    final String page = file(
        SHOP,
        head
            + "    Box b = new Box(); b.value = name; Printer.field(b, response);\n"
            + "    String[] a = new String[1]; a[0] = name; Printer.array(a, response);\n"
            + "    java.util.List<String> l = new java.util.ArrayList<>(); l.add(name);"
            + " Printer.list(l, response);\n  }\n"
            + "  void clean(HttpServletRequest request, HttpServletResponse response)"
            + " throws Exception {\n"
            + "    String name = HtmlUtils.htmlEscape(request.getParameter(\"n\"));\n"
            + "    Box b = new Box(); b.value = name; Printer.field(b, response);\n"
            + "    String[] a = new String[1]; a[0] = name; Printer.array(a, response);\n"
            + "    java.util.List<String> l = new java.util.ArrayList<>(); l.add(name);"
            + " Printer.list(l, response);\n  }\n}\n"
    );
    // When the taint rules run
    final List<AnalysisFinding> found =
        run(SHOP + "/Printer.java", printer, SHOP + "/Box.java", box, SHOP + "/Page.java", page);
    // Then the sink is reported, and a sanitised variant is not
    assertThat(found).hasSize(SINK_FLOWS);
    assertThat(found).allMatch(f -> f.line() < SANITISED_FROM_LINE);
  }

  @Test
  @DisplayName("classes with the same simple name")
  void classesWithTheSameSimpleName() {
    // Given two classes with the same simple name, method name and arity in different packages,
    // only one reaching a sink
    final String unsafe = file(
        "a",
        "public class Out {\n"
            + "  public static void put(String v, HttpServletResponse r) throws Exception {"
            + " r.getWriter().println(v); }\n}\n"
    );
    final String safe = file(
        "b",
        "public class Out {\n"
            + "  public static void put(String v, HttpServletResponse r) throws Exception {}\n}\n"
    );
    final String page = "package c;\n" + IMPORTS
        + "class Page {\n  void show(HttpServletRequest request, HttpServletResponse response)"
        + " throws Exception {\n    String name = request.getParameter(\"n\");\n"
        + "    a.Out.put(name, response);\n    b.Out.put(name, response);\n  }\n}\n";
    // When the taint rules run
    final List<AnalysisFinding> found =
        run("a/Out.java", unsafe, "b/Out.java", safe, "c/Page.java", page);
    // Then only the flow through that class is reported
    assertThat(found).hasSize(1);
    assertThat(found.get(0).line()).isEqualTo(UNSAFE_CALL_LINE);
  }
}
