package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

class TomlAnalysisCheckTest {

  @Test
  void parsesSelectedRuleIds() {
    final GateConfiguration check = check("rules = [\"ward:java/method-length\"]");

    assertThat(check.type()).isEqualTo("ward");
    assertThat(check.rules()).isEqualTo(List.of("ward:java/method-length"));
  }

  @Test
  void checkWithoutRulesSelectsEveryRule() {
    assertThat(check("").rules()).isEmpty();
  }

  @Test
  void rejectsUnknownKey() {
    assertThatThrownBy(() -> check("command = \"./run\""))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.command");
  }

  @Test
  void rejectsAThresholdKeyForASwiftRule() {
    assertThatThrownBy(() -> check("function-length = 60"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.function-length");
  }

  @Test
  void rejectsRuleIdOutsideTheWardNamespace() {
    assertThatThrownBy(() -> check("rules = [\"checkstyle:Foo\"]"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("ward rule ids");
  }

  @Test
  void rejectsEmptyRuleList() {
    assertThatThrownBy(() -> check("rules = []"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("must not be empty");
  }

  @Test
  void parsesTheJavaClasspathFile() {
    assertThat(check("java-classpath = \"build/classpath.txt\"").javaClasspath())
        .isEqualTo("build/classpath.txt");
  }

  @Test
  void checkWithoutJavaClasspathHasNone() {
    assertThat(check("").javaClasspath()).isNull();
  }

  @Test
  void rejectsBlankJavaClasspath() {
    assertThatThrownBy(() -> check("java-classpath = \"  \""))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.java-classpath");
  }

  private static GateConfiguration check(final String extra) {
    final String toml = "version = 2\n[checks.ward]\nuses = \"ward\"\n" + extra + "\n";
    return new TomlConfigurationReader().parse(Toml.parse(toml)).checks().get("ward");
  }
}
