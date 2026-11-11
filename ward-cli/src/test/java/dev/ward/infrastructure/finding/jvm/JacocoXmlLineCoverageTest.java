package dev.ward.infrastructure.finding.jvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineHits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JacocoXmlLineCoverageTest {

  private static final String REPORT =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
          + "<!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\">\n"
          + "<report name=\"x\"><sessioninfo id=\"s\" start=\"1\" dump=\"2\"/>\n"
          + "<package name=\"com/shop\">\n"
          + "<class name=\"com/shop/Cart\" sourcefilename=\"Cart.java\"><method name=\"a\" desc=\"()V\" line=\"3\">"
          + "<counter type=\"LINE\" missed=\"0\" covered=\"1\"/></method></class>\n"
          + "<sourcefile name=\"Cart.java\">"
          + "<line nr=\"3\" mi=\"0\" ci=\"4\" mb=\"0\" cb=\"0\"/>"
          + "<line nr=\"4\" mi=\"2\" ci=\"0\" mb=\"0\" cb=\"0\"/>"
          + "<counter type=\"LINE\" missed=\"1\" covered=\"1\"/></sourcefile>\n"
          + "<sourcefile name=\"Item.java\"><line nr=\"9\" mi=\"1\" ci=\"1\"/></sourcefile>\n"
          + "</package>\n"
          + "<package name=\"com/shop/util\"><sourcefile name=\"Math.java\">"
          + "<line nr=\"2\" mi=\"0\" ci=\"1\"/></sourcefile></package>\n"
          + "<counter type=\"LINE\" missed=\"3\" covered=\"3\"/></report>\n";

  @TempDir Path temp;

  @Test
  void readsCoveredMissedAndEveryPackage() throws IOException {
    final Path report = Files.writeString(temp.resolve("jacoco.xml"), REPORT);

    final LineHits hits = new JacocoXmlLineCoverage().read(report);

    assertThat(hits.entries())
        .extracting(LineHits.Entry::name)
        .containsExactly("com/shop/Cart.java", "com/shop/Item.java", "com/shop/util/Math.java");
    assertThat(hits.entries().get(0).kind()).isEqualTo(LineHits.Kind.PACKAGE_FILE);
    assertThat(hits.entries().get(0).lines().coverable()).containsExactly(3, 4);
    assertThat(hits.entries().get(0).lines().covered()).containsExactly(3);
    assertThat(hits.entries().get(1).lines().covered()).containsExactly(9);
    assertThat(hits.entries().get(2).lines().covered()).containsExactly(2);
  }

  @Test
  void aMalformedFileIsRejected() throws IOException {
    final Path report =
        Files.writeString(temp.resolve("jacoco.xml"), "<?xml version=\"1.0\"?><report><package");

    assertThatThrownBy(() -> new JacocoXmlLineCoverage().read(report))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("jacoco.xml");
  }

  @Test
  void aDifferentRootIsRejected() throws IOException {
    final Path report =
        Files.writeString(temp.resolve("other.xml"), "<?xml version=\"1.0\"?><pom/>");

    assertThatThrownBy(() -> new JacocoXmlLineCoverage().read(report))
        .isInstanceOf(LineCoverageException.class)
        .hasMessageContaining("not a JaCoCo report");
  }

  @Test
  void acceptsXmlHeadsOnly() {
    final JacocoXmlLineCoverage reader = new JacocoXmlLineCoverage();

    assertThat(reader.accepts("<?xml version")).isTrue();
    assertThat(reader.accepts("<report>")).isTrue();
    assertThat(reader.accepts("SF:x")).isFalse();
  }
}
