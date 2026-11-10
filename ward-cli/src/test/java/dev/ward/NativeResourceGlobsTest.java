package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every resource that a ward module packages ships in the native image only when a registered
 * resource glob matches it. The JVM tests read these files from the classpath either way, so a
 * missing glob shows up only in the native smoke tests.
 */
class NativeResourceGlobsTest {
  private static final Path REPOSITORY = Path.of("..");
  private static final Path METADATA =
      Path.of(
          "src/main/resources/META-INF/native-image/dev.ward/ward-cli/reachability-metadata.json");
  private static final String RESOURCES = "src/main/resources";
  private static final String NATIVE_IMAGE_CONFIG = "META-INF/native-image/";
  private static final String DOUBLE_STAR = "**";
  private static final Pattern GLOB = Pattern.compile("\"glob\":\\s*\"([^\"]+)\"");

  @Test
  void everyPackagedResourceMatchesANativeResourceGlob() throws IOException {
    final List<Pattern> globs = globs();
    final List<String> resources = packagedResources();

    assertThat(resources).isNotEmpty();
    for (final String resource : resources) {
      assertThat(globs)
          .as("native resource glob for %s", resource)
          .anyMatch(glob -> glob.matcher(resource).matches());
    }
  }

  /** Resource paths relative to each ward module's {@code src/main/resources}. */
  private static List<String> packagedResources() throws IOException {
    final List<String> resources = new ArrayList<>();
    try (Stream<Path> modules = Files.list(REPOSITORY)) {
      for (final Path module :
          modules.filter(path -> path.getFileName().toString().startsWith("ward-")).toList()) {
        resources.addAll(resourcesOf(module.resolve(RESOURCES)));
      }
    }
    return resources;
  }

  private static List<String> resourcesOf(final Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .map(file -> root.relativize(file).toString().replace('\\', '/'))
          .filter(resource -> !resource.startsWith(NATIVE_IMAGE_CONFIG))
          .toList();
    }
  }

  private static List<Pattern> globs() throws IOException {
    final Matcher matcher = GLOB.matcher(Files.readString(METADATA));
    return matcher.results().map(result -> toRegex(result.group(1))).toList();
  }

  /** Native-image globs: {@code **} spans folders, {@code *} stays inside one name. */
  private static Pattern toRegex(final String glob) {
    final StringBuilder regex = new StringBuilder();
    int index = 0;
    while (index < glob.length()) {
      if (glob.startsWith(DOUBLE_STAR, index)) {
        regex.append(".*");
        index += DOUBLE_STAR.length();
      } else {
        regex.append(
            glob.charAt(index) == '*' ? "[^/]*" : Pattern.quote(glob.substring(index, index + 1)));
        index++;
      }
    }
    return Pattern.compile(regex.toString());
  }
}
