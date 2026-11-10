package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SwiftProjectLayoutTest {

  private static final SwiftProjectLayout LAYOUT =
      new SwiftProjectLayout(
          List.of(
              new SwiftTarget("Shop", List.of("Modules/Shop"), List.of(), List.of()),
              new SwiftTarget("Kit", List.of("Modules"), List.of("Scripts/Kit.swift"), List.of()),
              new SwiftTarget("Inner", List.of("Modules/Shop/Inner"), List.of(), List.of())));

  @Test
  void aFileBelongsToTheTargetWhoseDirectoryHoldsItMostDeeply() {
    assertThat(LAYOUT.moduleOf("Modules/Shop/Inner/Item.swift")).isEqualTo("Inner");
    assertThat(LAYOUT.moduleOf("Modules/Shop/Cart.swift")).isEqualTo("Shop");
  }

  @Test
  void aListedFileBelongsToItsTargetWhateverItsDirectory() {
    assertThat(LAYOUT.moduleOf("Scripts/Kit.swift")).isEqualTo("Kit");
  }

  @Test
  void aListedFileStaysWithItsTargetWhenAnotherTargetOwnsItsDirectory() {
    final SwiftProjectLayout layout =
        new SwiftProjectLayout(
            List.of(
                new SwiftTarget("Inner", List.of("Modules/Shop/Inner"), List.of(), List.of()),
                new SwiftTarget(
                    "Shop",
                    List.of("Modules"),
                    List.of("Modules/Shop/Inner/Listed.swift"),
                    List.of())));

    assertThat(layout.moduleOf("Modules/Shop/Inner/Listed.swift")).isEqualTo("Shop");
    assertThat(layout.moduleOf("Modules/Shop/Inner/Other.swift")).isEqualTo("Inner");
  }

  @Test
  void aFileInNoTargetBelongsToTheImplicitModule() {
    assertThat(LAYOUT.moduleOf("Other/Main.swift")).isEqualTo(SwiftProjectLayout.IMPLICIT_MODULE);
  }

  @Test
  void onlyDeclaredTargetsCanBeImported() {
    assertThat(LAYOUT.declares("Shop")).isTrue();
    assertThat(LAYOUT.declares(SwiftProjectLayout.IMPLICIT_MODULE)).isFalse();
  }
}
