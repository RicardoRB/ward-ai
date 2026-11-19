package dev.ward.infrastructure.rule.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Runs the real Clippy on one fixture with only one lint on and reads the lines it reports. */
final class ClippyRun {
  private static final int LINE_GROUP = 1;
  private static final Pattern PRIMARY_SPAN =
      Pattern.compile(
          "\"line_start\":(\\d+),\"line_end\":\\d+,\"column_start\":\\d+,"
              + "\"column_end\":\\d+,\"is_primary\":true"
      );

  private ClippyRun() {}

  static List<Integer> reportedLines(final String clippy, final String id, final Path file)
      throws IOException, InterruptedException {
    final Path out = Files.createTempDirectory("ward-clippy-");
    try {
      final Process process =
          new ProcessBuilder(
              clippy,
              "--crate-type=lib",
              "--edition=2021",
              "--emit=metadata",
              "--error-format=json",
              "--out-dir=" + out,
              "--cap-lints=warn",
              "-Aclippy::all",
              "-W" + id.replace('-', '_'),
              file.toString()
          )
              .redirectErrorStream(true)
              .start();
      final String output =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      process.waitFor();
      return lines(output, id);
    } finally {
      try (Stream<Path> files = Files.walk(out)) {
        files.sorted(Comparator.reverseOrder()).forEach(ClippyRun::delete);
      }
    }
  }

  private static List<Integer> lines(final String output, final String id) {
    final String code = "\"code\":{\"code\":\"" + id.replace('-', '_') + "\"";
    final List<Integer> lines = new ArrayList<>();
    for (final String message : output.split("\n")) {
      final Matcher matcher = PRIMARY_SPAN.matcher(message);
      if (message.contains(code) && matcher.find()) {
        lines.add(Integer.parseInt(matcher.group(LINE_GROUP)));
      }
    }
    lines.sort(Comparator.naturalOrder());
    return lines.stream().distinct().toList();
  }

  private static void delete(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
