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
  @ValueSource(strings = {"7", "26", "27"})
  void jdkOutsideEightToTwentyFiveWarns(final String jdk) {
    assertThat(JavaSupport.warning(Integer.parseInt(jdk)))
        .contains(
            "Ward supports Java 8 to 25; the project builds on JDK "
                + jdk
                + ", and Ward may not work with it.");
  }
}
