package dev.ward.domain.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

/**
 * Compiles the baseline and the revision of every {@code api-compat} fixture, runs the real japicmp
 * on the two outputs and compares the types and members it reports as incompatible with those the
 * native {@code api-compat} check reports on the sources. Kotlin fixtures are compiled with {@code
 * kotlinc} and run only when {@code -Dward.kotlinc.binary} names it. Every difference must be
 * listed with its reason in {@code parity/japicmp-report.txt}, and every listed one must still
 * occur. Skipped unless {@code -Dward.parity.japicmp} names the japicmp classpath: the {@code
 * jar-with-dependencies} of japicmp 0.23.1, or its jar and dependencies joined by the path
 * separator. Nothing is resolved from the network.
 */
@EnabledIfSystemProperty(named = "ward.parity.japicmp", matches = ".+")
class ApiCompatJapicmpParitySmokeTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat");
  private static final Path REPORT = Path.of("src/test/resources/parity/japicmp-report.txt");
  private static final String JAPICMP_ONLY = "japicmp-only";
  private static final String WARD_ONLY = "ward-only";
  private static final String JAVA = "java";
  private static final String TAB = "\t";

  @TempDir Path work;

  @Test
  void everyDifferenceFromJapicmpIsListedWithItsReason()
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    final Set<String> differences = new TreeSet<>(differences(JAVA));
    if (JapicmpFixtureBuild.kotlinConfigured()) {
      differences.addAll(differences("kotlin"));
    }
    final Set<String> listed = listed(JapicmpFixtureBuild.kotlinConfigured());

    assertThat(differences).as("differences not listed in japicmp-report.txt").isSubsetOf(listed);
    assertThat(listed).as("listed differences that no longer occur").isSubsetOf(differences);
  }

  private Set<String> differences(final String language)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    final Set<String> differences = new TreeSet<>();
    final List<Path> fixtures;
    try (Stream<Path> directories = Files.list(ROOT.resolve(language))) {
      fixtures = directories.filter(Files::isDirectory).sorted().toList();
    }
    for (final Path fixture : fixtures) {
      differences.addAll(differences(language, fixture));
    }
    return differences;
  }

  private Set<String> differences(final String language, final Path fixture)
      throws IOException, InterruptedException, ParserConfigurationException, SAXException {
    final boolean kotlin = !JAVA.equals(language);
    final String name = language + "/" + fixture.getFileName();
    final Path old =
        JapicmpFixtureBuild.jar(kotlin, fixture.resolve("baseline"), work.resolve(name + "/old"));
    final Path revised =
        JapicmpFixtureBuild.jar(kotlin, fixture.resolve("revision"), work.resolve(name + "/new"));
    final Set<String> theirs =
        JapicmpRun.changes(kotlin, old, revised, work.resolve(name + "/out.xml"));
    final Set<String> ours = new TreeSet<>();
    for (final ApiChange change : ApiCompatFixturesTest.compare(fixture)) {
      ours.add(
          change.member().isEmpty()
              ? change.typeName()
              : change.typeName() + "#" + change.member());
    }
    final Set<String> differences = new TreeSet<>();
    theirs.stream()
        .filter(key -> !ours.contains(key))
        .forEach(key -> differences.add(String.join(TAB, name, JAPICMP_ONLY, key)));
    ours.stream()
        .filter(key -> !theirs.contains(key))
        .forEach(key -> differences.add(String.join(TAB, name, WARD_ONLY, key)));
    return differences;
  }

  /** The listed differences without their reason; Kotlin rows only when kotlinc is configured. */
  private static Set<String> listed(final boolean kotlin) throws IOException {
    final Set<String> listed = new TreeSet<>();
    for (final String line : Files.readAllLines(REPORT, StandardCharsets.UTF_8)) {
      if (!line.isBlank() && !line.startsWith("#") && (kotlin || line.startsWith("java/"))) {
        final String[] columns = line.split(TAB, -1);
        listed.add(String.join(TAB, List.of(columns).subList(0, columns.length - 1)));
      }
    }
    return listed;
  }
}
