package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import org.junit.jupiter.api.Test;

/** Checks each malformed-entry case of a taint specification, naming the file and the entry. */
class TaintSpecEntryTest {

  private static final String FILE = "taint/x/spec.toml";

  /** Scenario: Malformed entry. A sink with no argument positions names the file and entry. */
  @Test
  void sinkWithoutArgumentsIsRejectedWithItsEntry() {
    assertThatThrownBy(
            () -> TaintSpecFiles.parse(FILE, "[[sinks]]\nkind = \"call\"\nname = \"sink\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining(FILE)
        .hasMessageContaining("entry 1 of [sinks]")
        .hasMessageContaining("no argument positions");
  }

  @Test
  void unknownKindIsRejected() {
    assertThatThrownBy(
            () -> TaintSpecFiles.parse(FILE, "[[sources]]\nkind = \"variable\"\nname = \"x\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("kind 'variable'");
  }

  @Test
  void sinkKindMustBeACall() {
    assertThatThrownBy(
            () ->
                TaintSpecFiles.parse(
                    FILE, "[[sinks]]\nkind = \"member\"\nname = \"x\"\narguments = [0]\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("do not accept");
  }

  @Test
  void unknownKeyIsRejected() {
    assertThatThrownBy(
            () ->
                TaintSpecFiles.parse(
                    FILE, "[[sources]]\nkind = \"call\"\nname = \"x\"\nreceivr = \"y\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("unknown key 'receivr'");
  }

  @Test
  void missingNameIsRejected() {
    assertThatThrownBy(() -> TaintSpecFiles.parse(FILE, "[[sources]]\nkind = \"call\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("has no name");
  }

  @Test
  void argumentsOnASourceAreRejected() {
    assertThatThrownBy(
            () ->
                TaintSpecFiles.parse(
                    FILE, "[[sources]]\nkind = \"call\"\nname = \"x\"\narguments = [0]\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("only sinks take");
  }

  @Test
  void negativeArgumentPositionIsRejected() {
    assertThatThrownBy(
            () ->
                TaintSpecFiles.parse(
                    FILE, "[[sinks]]\nkind = \"call\"\nname = \"x\"\narguments = [-1]\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("not 0 or more");
  }

  @Test
  void propagatorFromMustNameAPositionOrReceiver() {
    assertThatThrownBy(
            () ->
                TaintSpecFiles.parse(
                    FILE,
                    "[[propagators]]\nkind = \"call\"\nname = \"x\"\nfrom = [\"qualifier\"]\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("neither a position nor 'receiver'");
  }
}
