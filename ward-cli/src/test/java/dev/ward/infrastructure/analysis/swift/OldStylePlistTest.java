package dev.ward.infrastructure.analysis.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OldStylePlistTest {

  @Test
  void readsDictionariesListsAndQuotedStringsAfterComments() {
    final Map<String, Object> plist =
        OldStylePlist.parse(
            """
            // !$*UTF8*$!
            {
                /* a block comment */
                name = "Shop \\"Main\\"";
                items = (First, "Second item",);
                nested = { path = Sources; };
            }
            """);

    assertThat(plist.get("name")).isEqualTo("Shop \"Main\"");
    assertThat(plist.get("items")).isEqualTo(List.of("First", "Second item"));
    assertThat(plist.get("nested")).isEqualTo(Map.of("path", "Sources"));
  }

  @Test
  void aTruncatedFileIsRejected() {
    assertThatThrownBy(() -> OldStylePlist.parse("{ objects = { id = {"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
