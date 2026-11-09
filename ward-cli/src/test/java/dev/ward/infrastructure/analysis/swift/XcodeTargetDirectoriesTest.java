package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class XcodeTargetDirectoriesTest {

  @Test
  void aTargetFilesInOneFolderOwnThatFolder() {
    assertThat(
            XcodeTargetDirectories.of(List.of("ShopKit/Domain/A.swift", "ShopKit/Domain/B.swift")))
        .containsExactly("ShopKit/Domain");
  }

  @Test
  void aTargetWithFilesInSiblingFoldersOwnsTheirDeepestCommonFolder() {
    assertThat(
            XcodeTargetDirectories.of(
                List.of("ShopKit/Domain/A.swift", "ShopKit/Application/B.swift")))
        .containsExactly("ShopKit");
  }

  @Test
  void aTargetWhoseCommonFolderIsTheRootOwnsNoFolder() {
    assertThat(XcodeTargetDirectories.of(List.of("App/main.swift", "ShopKit/Cart.swift")))
        .isEmpty();
    assertThat(XcodeTargetDirectories.of(List.of("main.swift", "ShopKit/Cart.swift"))).isEmpty();
  }

  @Test
  void aTargetWithNoFilesOwnsNoFolder() {
    assertThat(XcodeTargetDirectories.of(List.of())).isEmpty();
  }
}
