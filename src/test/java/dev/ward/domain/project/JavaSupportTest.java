package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaSupportTest {
  @ParameterizedTest
  @ValueSource(strings = {"8", "11", "17", "21", "25"})
  void supportedJdkHasNoWarning(final String jdk) {
    assertThat(JavaSupport.warning(Integer.parseInt(jdk))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"26", "27"})
  void jdkAbove25IsUntestedAndConfiguredAs25(final String jdk) {
    assertThat(JavaSupport.warning(Integer.parseInt(jdk)))
        .contains(
            "Ward has not been tested on JDK "
                + jdk
                + " and does not support it; it is configured as JDK 25.");
    assertThat(JavaSupport.selectionJdk(Integer.parseInt(jdk))).isEqualTo(JavaSupport.MAXIMUM_JDK);
  }

  @ParameterizedTest
  @ValueSource(strings = {"6", "7"})
  void jdkBelow8Warns(final String jdk) {
    assertThat(JavaSupport.warning(Integer.parseInt(jdk)))
        .contains(
            "Ward supports Java 8 to 25; the project builds on JDK "
                + jdk
                + ", and Ward may not work with it.");
  }
}
