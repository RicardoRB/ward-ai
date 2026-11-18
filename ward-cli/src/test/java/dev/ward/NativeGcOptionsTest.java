package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The native image's collector setting lives in the properties file every native build reads. */
class NativeGcOptionsTest {
  private static final String PROPERTIES =
      "/META-INF/native-image/dev.ward/ward-cli/native-image.properties";

  @Test
  @DisplayName("a cold run spends less time collecting")
  void nativeImageChoosesTheCollectionPolicyWithoutAFixedHeap() throws IOException {
    // Given
    final String properties;
    try (InputStream in = NativeGcOptionsTest.class.getResourceAsStream(PROPERTIES)) {
      properties = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    // When
    final String args =
        properties.lines().filter(line -> !line.startsWith("#")).collect(Collectors.joining(" "));

    // Then
    assertThat(args).contains("-R:InitialCollectionPolicy=BySpaceAndTime");
  }

  @Test
  @DisplayName("a small machine")
  void nativeImageBuildsNoMinimumOrMaximumHeapIn() throws IOException {
    // Given
    final String properties;
    try (InputStream in = NativeGcOptionsTest.class.getResourceAsStream(PROPERTIES)) {
      properties = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    // When
    final String args =
        properties.lines().filter(line -> !line.startsWith("#")).collect(Collectors.joining(" "));

    // Then
    assertThat(args).doesNotContain("MinHeapSize").doesNotContain("Xms").doesNotContain("Xmn");
  }
}
