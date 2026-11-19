package dev.ward.domain.gate.cargo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CargoMembersTest {
  @Test
  @DisplayName("the members of a workspace are listed")
  void listsMembers() {
    final String manifest =
        "[workspace]\nresolver = \"2\"\nmembers = [\n  \"core\",\n  \"./crates/*\", # all\n  \"tools/cli/\",\n]\n\n[dependencies]\nmembers = [\"x\"]\n";

    assertThat(CargoMembers.listed(manifest)).containsExactly("core", "crates/*", "tools/cli");
  }

  @Test
  @DisplayName("a package manifest has no members")
  void packageHasNone() {
    assertThat(CargoMembers.listed("[package]\nname = \"a\"\n")).isEmpty();
  }
}
