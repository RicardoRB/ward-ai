package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.TaintCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks loading a specification file, a missing file, and a taint rule body. */
class TaintSpecFilesTest {

  private static final String FILE = "taint/x/spec.toml";

  /** A valid file yields one entry per table row, with its kind, receiver and arguments. */
  @Test
  void validFileParsesEveryTable() {
    final TaintSpec spec = TaintSpecFiles.load(List.of("taint/fake/java-sql.toml"));
    assertThat(spec.sources()).hasSize(2);
    assertThat(spec.sources().get(1).kind()).isEqualTo(FlowSiteKind.MEMBER_READ);
    assertThat(spec.sources().get(1).qualifier()).contains("req");
    assertThat(spec.sinks().get(0).arguments()).containsExactly(0);
    assertThat(spec.sanitizers()).hasSize(1);
    assertThat(spec.propagators()).hasSize(1);
  }

  /** A member-write sink is accepted, and as a source it is a configuration error. */
  @Test
  void memberWriteIsAcceptedAsASinkOnly() {
    final TaintSpec spec =
        TaintSpecFiles.parse(
            FILE, "[[sinks]]\nkind = \"member-write\"\nname = \"innerHTML\"\narguments = [0]\n");
    assertThat(spec.sinks().get(0).kind()).isEqualTo(FlowSiteKind.MEMBER_WRITE);
    assertThatThrownBy(
            () ->
                TaintSpecFiles.parse(
                    FILE, "[[sources]]\nkind = \"member-write\"\nname = \"innerHTML\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("entry 1 of [sources] in taint/x/spec.toml")
        .hasMessageContaining("member-write");
  }

  @Test
  void invalidTomlIsRejected() {
    assertThatThrownBy(() -> TaintSpecFiles.parse(FILE, "[[sources\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("not valid TOML");
  }

  @Test
  void unknownTableIsRejected() {
    assertThatThrownBy(
            () -> TaintSpecFiles.parse(FILE, "[[filters]]\nkind = \"call\"\nname = \"x\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("unknown table 'filters'");
  }

  @Test
  void missingResourceIsRejected() {
    assertThatThrownBy(() -> TaintSpecFiles.load(List.of("taint/none/missing.toml")))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("is not bundled");
  }

  /** A taint rule body compiles to a check, and a body without specs is a configuration error. */
  @Test
  void taintBodyCompilesToACheck() {
    assertThat(
            DeclarativeChecks.bundled()
                .build(
                    "check = \"taint\"\nspecs = [\"taint/fake/java-sql.toml\"]\nmessage = \"x\""))
        .isInstanceOf(TaintCheck.class);
    assertThatThrownBy(
            () -> DeclarativeChecks.bundled().build("check = \"taint\"\nmessage = \"x\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("specs must name");
  }

  /** A string sink with a pattern is a string entry, and the pattern finds an HTML tag. */
  @Test
  void stringSinkWithPatternIsAStringEntry() {
    final TaintSpec spec =
        TaintSpecFiles.parse(FILE, "[[sinks]]\nkind = \"string\"\npattern = \"<[a-zA-Z]\"\n");

    assertThat(spec.sinks().get(0).kind()).isEqualTo(FlowSiteKind.STRING_BUILD);
    assertThat(spec.sinks().get(0).pattern().orElseThrow().matcher("<p>").find()).isTrue();
  }

  /** A string sink must name its pattern. */
  @Test
  void stringSinkWithoutPatternIsAnError() {
    assertThatThrownBy(() -> TaintSpecFiles.parse(FILE, "[[sinks]]\nkind = \"string\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining(FILE);
  }

  /** A pattern is only for string sinks. */
  @Test
  void patternOnACallSinkIsAnError() {
    assertThatThrownBy(
            () ->
                TaintSpecFiles.parse(
                    FILE,
                    "[[sinks]]\nkind = \"call\"\nname = \"run\"\narguments = [0]\n"
                        + "pattern = \"<\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("pattern");
  }

  /** A pattern that does not compile is a configuration error naming the entry. */
  @Test
  void invalidPatternIsAnError() {
    assertThatThrownBy(
            () -> TaintSpecFiles.parse(FILE, "[[sinks]]\nkind = \"string\"\npattern = \"<[\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("invalid pattern");
  }
}
