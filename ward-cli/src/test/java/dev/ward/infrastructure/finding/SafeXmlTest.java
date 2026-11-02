package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafeXmlTest {
  private static final String ENTITY_DOCUMENT =
      """
      <?xml version="1.0"?>
      <!DOCTYPE root [<!ENTITY secret "expanded">]>
      <root><item>&secret;</item></root>
      """;

  private static List<String> elements(final String xml) {
    final List<String> names = new ArrayList<>();
    SafeXml.read(xml, reader -> names.add(reader.getLocalName()));
    return names;
  }

  @Test
  void readsElementsFromAString() {
    assertThat(elements("<checkstyle><file/><error/></checkstyle>"))
        .containsExactly("checkstyle", "file", "error");
  }

  @Test
  void doesNotExpandADoctypeEntity() {
    assertThat(elements(ENTITY_DOCUMENT)).doesNotContain("expanded");
  }

  @Test
  void malformedXmlFromAStringThrows() {
    assertThatThrownBy(() -> elements("<checkstyle><file></checkstyle>"))
        .isInstanceOf(MalformedReportException.class)
        .hasMessage("Malformed report on standard output");
  }
}
