package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The corpus CI job downloads only archives that match the pins of the smoke tests. */
class CorpusArchivesTest {
  private static final Path MANIFEST = Path.of("src/test/resources/corpus/archives.txt");
  private static final Path SCRIPT = Path.of("src/test/resources/corpus/fetch-corpora.sh");
  private static final int TIMEOUT_SECONDS = 60;

  @TempDir private Path temp;

  @Test
  @DisplayName("a tampered archive")
  void aTamperedArchive() throws Exception {
    // Given an archive whose sha-256 differs from the pinned one
    final Path archive = temp.resolve("served.tar.gz");
    writeTar(archive);
    final String pinned = "0".repeat(64);
    final Path manifest = temp.resolve("manifest.txt");
    Files.writeString(manifest, "java commons " + pinned + " file://" + archive + "\n");

    // When the job downloads it
    final Run run = fetch(manifest);

    // Then it fails before analysis
    assertThat(run.exit()).isNotZero();
    assertThat(run.output()).contains("sha-256 mismatch");
    assertThat(temp.resolve("out/java")).doesNotExist();
  }

  @Test
  @DisplayName("an archive with the pinned sha-256 is unpacked")
  void anArchiveWithThePinnedHashIsUnpacked() throws Exception {
    // Given an archive whose sha-256 is the pinned one
    final Path archive = temp.resolve("served.tar.gz");
    writeTar(archive);
    final Path manifest = temp.resolve("manifest.txt");
    Files.writeString(
        manifest,
        "java commons " + JulietCorpus.sha256(archive) + " file://" + archive + "\n"
    );

    // When the job downloads it
    final Run run = fetch(manifest);

    // Then it unpacks the archive and links it for the smoke test's own check
    assertThat(run.exit()).as(run.output()).isZero();
    assertThat(temp.resolve("out/java/top/File.java")).exists();
    assertThat(temp.resolve("out/archives/java/commons.tar.gz")).exists();
  }

  @Test
  @DisplayName("the manifest holds the pins of the smoke tests")
  void theManifestHoldsThePinsOfTheSmokeTests() throws IOException {
    // Given the manifest of the corpus job
    final Map<String, String> manifest = new TreeMap<>();
    for (final String line : Files.readAllLines(MANIFEST)) {
      final String[] fields = line.split(" ");
      manifest.put(fields[1], fields[2]);
    }

    // When it is compared with the pins the smoke tests hold
    final Map<String, String> pins = new TreeMap<>();
    final List<PinnedCorpus> jvm = new ArrayList<>(PinnedCorpus.java());
    jvm.addAll(PinnedCorpus.kotlin());
    jvm.addAll(PinnedCorpus.csharp());
    jvm.addAll(PinnedCorpus.rust());
    jvm.addAll(PinnedCorpus.python());
    jvm.addAll(PinnedCorpus.node());
    jvm.addAll(PinnedCorpus.swift());
    jvm.forEach(corpus -> pins.put(corpus.name(), corpus.archiveSha256()));
    SwiftCorpus.all().forEach(corpus -> pins.put(corpus.name(), corpus.archiveSha256()));
    pins.put("healthchecks", PythonCorpus.HEALTHCHECKS.sha256());
    pins.put("ctfd", FlaskCorpus.ARCHIVE_SHA256);
    pins.put("got", GotBenchmarkSmokeTest.ARCHIVE_SHA256);
    pins.put("juice-shop", JuiceShopBenchmarkSmokeTest.ARCHIVE_SHA256);

    // Then every corpus is listed with its pinned hash
    assertThat(manifest).isEqualTo(pins);
  }

  private Run fetch(final Path manifest) throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
            "sh",
            SCRIPT.toString(),
            manifest.toString(),
            temp.resolve("cache").toString(),
            temp.resolve("out").toString()
        )
            .redirectErrorStream(true)
            .start();
    final String output = new String(
        process.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8
    );
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Run(process.exitValue(), output);
  }

  /** Writes a tar.gz with one file below {@code top/}, by calling the system tar. */
  private void writeTar(final Path archive) throws IOException, InterruptedException {
    final Path source = temp.resolve("src/top");
    Files.createDirectories(source);
    Files.writeString(source.resolve("File.java"), "class File {}\n");
    final Process tar =
        new ProcessBuilder(
            "tar",
            "-czf",
            archive.toString(),
            "-C",
            temp.resolve("src").toString(),
            "top"
        )
            .inheritIO()
            .start();
    assertThat(tar.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    assertThat(tar.exitValue()).isZero();
  }

  private record Run(int exit, String output) {}
}
