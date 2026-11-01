package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SwiftSupportTest {
  @ParameterizedTest
  @ValueSource(strings = {"16.2", "15.4", "25.9"})
  void olderXcodeIsIncompatible(final String version) {
    assertThat(SwiftSupport.xcodeIncompatibility(version))
        .hasValue("Xcode " + version + " is INCOMPATIBLE; Ward needs Xcode 26.0 or newer.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"26.0", "26.0.1", "26.6", "27.2", "100.0"})
  void supportedXcodeIsNotIncompatible(final String version) {
    assertThat(SwiftSupport.xcodeIncompatibility(version)).isEmpty();
  }

  @Test
  void versionThatIsNotMajorMinorIsNotIncompatible() {
    assertThat(SwiftSupport.xcodeIncompatibility("beta")).isEmpty();
  }
}
