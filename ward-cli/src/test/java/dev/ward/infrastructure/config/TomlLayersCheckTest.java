package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** The {@code layers} tables of a {@code ward} check. */
class TomlLayersCheckTest {

  @Test
  void parsesLayerBoundaries() {
    final GateConfiguration check =
        check("layers = [{ from = [\"domain\", \"application\"], forbidden = [\"adapter\"] }]");

    assertThat(check.layers())
        .containsExactly(new LayerBoundary(List.of("domain", "application"), List.of("adapter")));
  }

  @Test
  void checkWithoutLayersHasNoBoundaries() {
    assertThat(check("").layers()).isEmpty();
  }

  @Test
  void rejectsEmptyForbiddenListNamingTheKey() {
    assertThatThrownBy(() -> check("layers = [{ from = [\"domain\"], forbidden = [] }]"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.layers.forbidden");
  }

  @Test
  void rejectsUnknownBoundaryKey() {
    assertThatThrownBy(
            () -> check("layers = [{ from = [\"domain\"], forbidden = [\"x\"], color = \"red\" }]"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.layers.color");
  }

  @Test
  void rejectsLayerNameThatIsNotADirectoryName() {
    assertThatThrownBy(() -> check("layers = [{ from = [\"do/main\"], forbidden = [\"x\"] }]"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.layers");
  }

  private static GateConfiguration check(final String extra) {
    final String toml = "version = 2\n[checks.ward]\nuses = \"ward\"\n" + extra + "\n";
    return new TomlConfigurationReader().parse(Toml.parse(toml)).checks().get("ward");
  }
}
