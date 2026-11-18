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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cache key of a unit depends on the taint summaries of its dependencies, not on their text:
 * an edit that keeps a callee's summary analyzes only the edited file, and an edit that changes it
 * analyzes the dependents again. Every cached run is compared with a cold run of the same files.
 */
class TaintSummaryCacheTest {
  private static final String END = "}\n";
  private static final String END_METHOD = "  }\n";
  private static final int CHAIN = 3;
  private static final String RULE = "ward:java/cross-site-scripting";
  private static final String ENTRY = "src/main/java/app/Entry.java";
  private static final String RELAY = "src/main/java/app/Relay.java";
  private static final String WRITER = "src/main/java/app/Writer.java";
  private static final String HANDLER = "src/main/java/app/Handler.java";
  private static final String FIRST = "src/main/java/app/FirstHandler.java";
  private static final String SECOND = "src/main/java/app/SecondHandler.java";
  private static final String CALLER = "src/main/java/app/Caller.java";
  private static final String IMPORTS =
      "package app;\nimport javax.servlet.http.HttpServletRequest;\n"
          + "import javax.servlet.http.HttpServletResponse;\n"
          + "import org.springframework.web.util.HtmlUtils;\n";
  private static final String ENTRY_TEXT =
      IMPORTS
          + "class Entry {\n"
          + "  void show(HttpServletRequest request, HttpServletResponse response) throws Exception {\n"
          + "    String name = request.getParameter(\"n\");\n"
          + "    (new Relay()).pass(name, response);\n"
          + END_METHOD
          + END;
  private static final String RELAY_TEXT =
      IMPORTS
          + "class Relay {\n"
          + "  void pass(String value, HttpServletResponse response) throws Exception {\n"
          + "    Writer.emit(value, response);\n"
          + END_METHOD
          + END;
  private static final String WRITER_UNSAFE =
      IMPORTS
          + "class Writer {\n"
          + "  static void emit(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.getWriter().println(value);\n"
          + END_METHOD
          + END;
  private static final String WRITER_SAFE =
      IMPORTS
          + "class Writer {\n"
          + "  static void emit(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.getWriter().println(HtmlUtils.htmlEscape(value));\n"
          + END_METHOD
          + END;
  private static final String WRITER_REWRITTEN =
      IMPORTS
          + "class Writer {\n"
          + "  static void emit(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.setContentType(\"text/html\");\n"
          + "    response.getWriter().println(HtmlUtils.htmlEscape(value));\n"
          + "    response.flushBuffer();\n"
          + END_METHOD
          + END;
  private static final String HANDLER_TEXT =
      IMPORTS
          + "interface Handler {\n"
          + "  void handle(String value, HttpServletResponse response) throws Exception;\n"
          + END;
  private static final String FIRST_TEXT =
      IMPORTS
          + "class FirstHandler implements Handler {\n"
          + "  public void handle(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.getWriter().println(value);\n"
          + END_METHOD
          + END;
  private static final String SECOND_TEXT =
      IMPORTS
          + "class SecondHandler implements Handler {\n"
          + "  public void handle(String value, HttpServletResponse response) throws Exception {\n"
          + "    response.getWriter().println(HtmlUtils.htmlEscape(value));\n"
          + END_METHOD
          + END;
  private static final String CALLER_TEXT =
      IMPORTS
          + "class Caller {\n"
          + "  void show(HttpServletRequest request, HttpServletResponse response, Handler handler)\n"
          + "      throws Exception {\n"
          + "    String name = request.getParameter(\"n\");\n"
          + "    handler.handle(name, response);\n"
          + END_METHOD
          + END;

  @TempDir Path root;

  private final Map<String, String> entries = new HashMap<>();
  private final List<String> resultWrites = new ArrayList<>();
  private final AnalysisCache warm = recording(AnalysisCacheFixtures.memory(entries));
  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());
  private final List<RuleDefinition> rules =
      new ClasspathRuleSource()
          .load().rules().stream().filter(rule -> RULE.equals(rule.id())).toList();

  /** Notes each write of a unit's findings, so a test can tell which units were analyzed. */
  private AnalysisCache recording(final AnalysisCache inner) {
    return new AnalysisCache() {
      @Override
      public Optional<String> read(final String key) {
        return inner.read(key);
      }

      @Override
      public void write(final String key, final String value) {
        inner.write(key, value);
        if (AnalysisCacheFixtures.codec().decode(key, value).isPresent()) {
          resultWrites.add(key);
        }
      }
    };
  }

  private List<AnalysisFinding> run(final AnalysisCache cache, final List<SourceUnit> units) {
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher()),
        AnalysisCacheFixtures.codec(),
        "test",
        new JvmDependencyAdapters().forProject(root, units)
    )
        .run(cache, units, frontends, rules)
        .findings();
  }

  private static List<SourceUnit> chain(final String writer) {
    return List.of(unit(ENTRY, ENTRY_TEXT), unit(RELAY, RELAY_TEXT), unit(WRITER, writer));
  }

  private static List<SourceUnit> handlers(final boolean second) {
    final List<SourceUnit> units =
        new ArrayList<>(
            List.of(unit(CALLER, CALLER_TEXT), unit(HANDLER, HANDLER_TEXT), unit(FIRST, FIRST_TEXT))
        );
    if (second) {
      units.add(unit(SECOND, SECOND_TEXT));
    }
    return units;
  }

  private static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.JAVA, text);
  }

  @Test
  @DisplayName("an edit that keeps the summary")
  void anEditThatKeepsTheSummary() {
    // Given a cached run and a comment or a function body edited in a callee without changing its
    // summary
    final List<AnalysisFinding> first = run(warm, chain(WRITER_SAFE));
    resultWrites.clear();

    // When ward check runs again
    final List<SourceUnit> edited = chain("// a note\n" + WRITER_REWRITTEN);
    final List<AnalysisFinding> second = run(warm, edited);

    // Then only the edited file is analyzed again
    assertThat(resultWrites).hasSize(1);
    assertThat(second).isEqualTo(first).isEqualTo(run(AnalysisCacheFixtures.NO_CACHE, edited));
  }

  @Test
  @DisplayName("an edit that changes the summary")
  void anEditThatChangesTheSummary() {
    // Given a cached run and a callee edited so that it now passes its argument to a sink
    final List<AnalysisFinding> first = run(warm, chain(WRITER_SAFE));
    resultWrites.clear();

    // When ward check runs again
    final List<SourceUnit> edited = chain(WRITER_UNSAFE);
    final List<AnalysisFinding> second = run(warm, edited);

    // Then its dependents are analyzed again and the new flow is reported
    assertThat(first).isEmpty();
    assertThat(resultWrites).hasSize(CHAIN);
    assertThat(second).hasSize(1).isEqualTo(run(AnalysisCacheFixtures.NO_CACHE, edited));
    assertThat(second.get(0).file()).isEqualTo(ENTRY);
  }

  @Test
  @DisplayName("an implementor added in another file")
  void anImplementorAddedInAnotherFile() {
    // Given a cached run where a call on an interface reaches its one implementor's sink
    final List<AnalysisFinding> first = run(warm, handlers(false));
    resultWrites.clear();

    // When a second implementor is added in another file and ward check runs again
    final List<SourceUnit> added = handlers(true);
    final List<AnalysisFinding> second = run(warm, added);

    // Then the caller is analyzed again and, as cold, the call is no longer followed
    assertThat(first).hasSize(1);
    assertThat(second).isEmpty();
    assertThat(run(AnalysisCacheFixtures.NO_CACHE, added)).isEmpty();
    assertThat(resultWrites).hasSizeGreaterThanOrEqualTo(2);
  }
}
