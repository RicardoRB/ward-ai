package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Reads the labels of a checked-in corpus and rejects malformed rows. */
class LabelCsvTest {

  @Test
  void readsOneLabelPerRow() {
    final List<AccuracyLabel> labels =
        LabelCsv.parse(
            List.of(
                "file,rule,vulnerable",
                "Search.java,ward:java/sql-injection,true",
                "Safe.java,ward:java/sql-injection,false"));

    assertThat(labels)
        .containsExactly(
            new AccuracyLabel("Search.java", "ward:java/sql-injection", true),
            new AccuracyLabel("Safe.java", "ward:java/sql-injection", false));
  }

  @Test
  void rejectsAVulnerableValueThatIsNotBoolean() {
    assertThatThrownBy(
            () ->
                LabelCsv.parse(
                    List.of("file,rule,vulnerable", "A.java,ward:java/sql-injection,yes")))
        .hasMessageContaining("true or false");
  }

  @Test
  void rejectsARuleThatIsNotAWardId() {
    assertThatThrownBy(() -> LabelCsv.parse(List.of("file,rule,vulnerable", "A.java,sql,true")))
        .hasMessageContaining("ward rule id");
  }

  @Test
  void rejectsAMissingHeader() {
    assertThatThrownBy(() -> LabelCsv.parse(List.of("A.java,ward:java/sql-injection,true")))
        .hasMessageContaining("header");
  }
}
