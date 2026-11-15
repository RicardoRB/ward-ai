package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The flow digest of a file ignores layout and comments, and follows its code. */
class FlowDigestsTest {
  private static final String PATH = "src/A.java";
  private static final String CODE =
      "class A {\n  void run(String v) {\n    sink(v);\n    other(v);\n  }\n}\n";

  private final Map<String, String> entries = new HashMap<>();
  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private String digest(final String text) {
    return new FlowDigests(AnalysisCacheFixtures.memory(entries), "test", frontends)
        .of(List.of(new SourceUnit(PATH, AnalysisLanguage.JAVA, text)))
        .get(0)
        .digest();
  }

  @Test
  void commentsBlankLinesAndIndentationDoNotChangeTheDigest() {
    final String laidOut =
        "// header\n\nclass A {\n\n      void run( String v ) {\n   // note\n sink(v);\n\n"
            + "    other(v); // trailing\n  }\n}\n/* end */\n";

    assertThat(digest(laidOut)).isEqualTo(digest(CODE));
  }

  @Test
  void aChangedCallOrArgumentChangesTheDigest() {
    assertThat(digest(CODE.replace("sink(v)", "sink2(v)"))).isNotEqualTo(digest(CODE));
    assertThat(digest(CODE.replace("other(v)", "other(\"x\")"))).isNotEqualTo(digest(CODE));
    assertThat(digest(CODE.replace("    other(v);\n", ""))).isNotEqualTo(digest(CODE));
  }

  @Test
  void aDigestIsStoredAndACorruptOneIsComputedAgain() {
    final String first = digest(CODE);
    assertThat(entries.values()).contains(first);
    entries.replaceAll((key, value) -> "not a digest");

    assertThat(digest(CODE)).isEqualTo(first);
    assertThat(entries.values()).contains(first);
  }

  @Test
  void filesThatDoNotParseHaveNoGraphsAndOneDigest() {
    assertThat(digest("class A { void run( { }")).isEqualTo(digest("class {"));
    assertThat(digest("class {")).isNotEqualTo(digest(CODE));
  }
}
