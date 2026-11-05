package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** The {@code cycles} and {@code independence} tables of a {@code ward} check. */
class TomlArchitectureCheckTest {

  @Test
  void parsesCycleRootsAndNormalizesThem() {
    final GateConfiguration check =
        check("[checks.ward.cycles]\nroots = [\"./src/main/java/com/acme/\", \"src\\\\app\"]");

    assertThat(check.cycleRoots()).containsExactly("src/main/java/com/acme", "src/app");
  }

  @Test
  void parsesIndependenceTables() {
    final GateConfiguration check =
        check(
            "[[checks.ward.independence]]\nmodules = [\"shop/orders\", \"shop/billing\"]\n"
                + "[[checks.ward.independence]]\nmodules = [\"a\", \"b\", \"c\"]");

    assertThat(check.independence())
        .containsExactly(List.of("shop/orders", "shop/billing"), List.of("a", "b", "c"));
  }

  @Test
  void checkWithoutTheTablesSetsNone() {
    final GateConfiguration check = check("");

    assertThat(check.cycleRoots()).isEmpty();
    assertThat(check.independence()).isEmpty();
  }

  @Test
  void rejectsEmptyRootsNamingTheKey() {
    assertThatThrownBy(() -> check("[checks.ward.cycles]\nroots = []"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.cycles.roots");
  }

  @Test
  void rejectsARootOutsideTheProject() {
    assertThatThrownBy(() -> check("[checks.ward.cycles]\nroots = [\"../other\"]"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.cycles.roots");
  }

  @Test
  void rejectsUnknownCycleKey() {
    assertThatThrownBy(() -> check("[checks.ward.cycles]\nroots = [\"src\"]\ndepth = 2"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.cycles.depth");
  }

  @Test
  void rejectsIndependenceTableWithOneModule() {
    assertThatThrownBy(() -> check("[[checks.ward.independence]]\nmodules = [\"shop/orders\"]"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.independence.modules");
  }

  @Test
  void rejectsUnknownIndependenceKey() {
    assertThatThrownBy(
            () -> check("[[checks.ward.independence]]\nmodules = [\"a\", \"b\"]\nsoft = true"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.ward.independence.soft");
  }

  private static GateConfiguration check(final String extra) {
    final String toml = "version = 2\n[checks.ward]\nuses = \"ward\"\n" + extra + "\n";
    return new TomlConfigurationReader().parse(Toml.parse(toml)).checks().get("ward");
  }
}
