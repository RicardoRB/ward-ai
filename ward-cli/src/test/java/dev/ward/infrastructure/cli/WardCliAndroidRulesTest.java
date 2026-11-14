package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code ward} gate reads the manifest and the layouts of an Android module. */
class WardCliAndroidRulesTest extends WardCliIntegrationSupport {
  private static final String MANIFEST =
      """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application android:debuggable="true" android:allowBackup="false" />
      </manifest>
      """;
  private static final String LAYOUT =
      """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <ImageView android:src="@drawable/logo" />
      </LinearLayout>
      """;

  @TempDir Path source;

  private void androidProject() throws IOException {
    Files.writeString(
        source.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>a</groupId>"
            + "<artifactId>a</artifactId><version>1</version></project>\n");
    write("app/src/main/AndroidManifest.xml", MANIFEST);
    write("app/src/main/res/layout/main.xml", LAYOUT);
    write("app/src/main/resources/res/other.xml", LAYOUT);
    Files.writeString(
        source.resolve(WARD_TOML),
        """
        version = 2
        [checks.android]
        uses = "ward"
        rules = ["ward:android/debuggable-application", "ward:android/missing-content-description"]
        """);
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = source.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  @Test
  void theManifestAndTheLayoutAreCheckedAndOtherXmlIsNot() throws IOException {
    androidProject();

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", source.toString()))
        .isEqualTo(ExitCodes.FAILED);

    assertThat(out.toString())
        .contains("ward:android/debuggable-application", "app/src/main/AndroidManifest.xml")
        .contains("ward:android/missing-content-description", "app/src/main/res/layout/main.xml")
        .doesNotContain("res/other.xml");
  }
}
