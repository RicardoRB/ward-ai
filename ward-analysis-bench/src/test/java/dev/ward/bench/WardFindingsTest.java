package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Reads the findings of the ward check's JSON and refuses output that is not a complete run. */
class WardFindingsTest {

  private static final String FINDING_JSON =
      "{\"schemaVersion\":2,\"status\":\"FAILED\",\"checks\":[{\"name\":\"ward-accuracy\","
          + "\"type\":\"ward\",\"status\":\"FAILED\",\"findings\":["
          + "{\"tool\":\"ward\",\"rule\":\"ward:java/sql-injection\",\"file\":\"Search.java\","
          + "\"line\":6,\"column\":15,\"message\":\"m\"},"
          + "{\"tool\":\"ward\",\"rule\":\"ward:java/sql-injection\",\"file\":\"Search.java\","
          + "\"line\":9,\"column\":3,\"message\":\"m\"}],\"findingsTruncated\":false}]}";

  @Test
  void countsEachFileOncePerRule() {
    assertThat(WardFindings.read(FINDING_JSON))
        .containsExactly(new WardFinding("Search.java", "ward:java/sql-injection"));
  }

  @Test
  void aPassedCheckHasNoFindings() {
    assertThat(
            WardFindings.read(
                "{\"checks\":[{\"status\":\"PASSED\",\"findings\":[],\"findingsTruncated\":false}]}"))
        .isEmpty();
  }

  @Test
  void aRunWithoutChecksIsAnError() {
    assertThatThrownBy(() -> WardFindings.read("{\"status\":\"ERROR\",\"error\":\"no project\"}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no project");
  }

  @Test
  void aCheckThatDidNotRunIsAnError() {
    assertThatThrownBy(
            () -> WardFindings.read("{\"checks\":[{\"status\":\"UNAVAILABLE\",\"findings\":[]}]}"))
        .hasMessageContaining("did not run");
  }

  @Test
  void truncatedFindingsAreAnError() {
    assertThatThrownBy(
            () ->
                WardFindings.read(
                    "{\"checks\":[{\"status\":\"FAILED\",\"findings\":[],"
                        + "\"findingsTruncated\":true}]}"))
        .hasMessageContaining("truncated");
  }
}
