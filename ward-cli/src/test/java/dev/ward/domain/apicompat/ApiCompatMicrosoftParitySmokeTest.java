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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Packs the baseline and the revision of every C# {@code api-compat} fixture, lets the real
 * Microsoft.DotNet.ApiCompat of the .NET SDK compare the packages, and compares the types and
 * members it reports with those the native check reports on the sources. Every difference must be
 * listed with its reason in {@code parity/dotnet-apicompat-report.txt}, and every listed one must
 * still occur. Skipped unless {@code -Dward.parity.apicompat} names the {@code dotnet} executable
 * of an SDK that bundles ApiCompat (.NET 8 or later); the packs restore nothing from the network.
 */
@EnabledIfSystemProperty(named = "ward.parity.apicompat", matches = ".+")
class ApiCompatMicrosoftParitySmokeTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat/csharp");
  private static final Path REPORT =
      Path.of("src/test/resources/parity/dotnet-apicompat-report.txt");
  private static final String TAB = "\t";

  @TempDir Path work;

  @Test
  @DisplayName("parity with Microsoft.DotNet.ApiCompat")
  void everyDifferenceFromApiCompatIsListedWithItsReason()
      throws IOException, InterruptedException {
    final DotnetApiCompatRun tool = new DotnetApiCompatRun(
        System.getProperty("ward.parity.apicompat")
    );
    final Set<String> differences = new TreeSet<>();
    final List<Path> fixtures;
    try (Stream<Path> directories = Files.list(ROOT)) {
      fixtures = directories.filter(Files::isDirectory).sorted().toList();
    }
    for (final Path fixture : fixtures) {
      final String name = fixture.getFileName().toString();
      final Set<String> theirs =
          tool.changes(
              fixture.resolve("baseline"),
              fixture.resolve("revision"),
              work.resolve(name)
          );
      final Set<String> ours = new TreeSet<>();
      for (final ApiChange change : ApiCompatFixturesTest.compare(fixture)) {
        ours.add(
            change.member().isEmpty()
                ? change.typeName()
                : change.typeName() + "#" + change.member()
        );
      }
      theirs.stream()
          .filter(key -> !ours.contains(key))
          .forEach(key -> differences.add(String.join(TAB, name, "apicompat-only", key)));
      ours.stream()
          .filter(key -> !theirs.contains(key))
          .forEach(key -> differences.add(String.join(TAB, name, "ward-only", key)));
    }

    final Set<String> listed = listed();
    assertThat(differences).as("differences not listed in dotnet-apicompat-report.txt").isSubsetOf(
        listed
    );
    assertThat(listed).as("listed differences that no longer occur").isSubsetOf(differences);
  }

  /** The listed differences without their reason. */
  private static Set<String> listed() throws IOException {
    final Set<String> listed = new TreeSet<>();
    for (final String line : Files.readAllLines(REPORT, StandardCharsets.UTF_8)) {
      if (!line.isBlank() && !line.startsWith("#")) {
        final String[] columns = line.split(TAB, -1);
        listed.add(String.join(TAB, List.of(columns).subList(0, columns.length - 1)));
      }
    }
    return listed;
  }
}
