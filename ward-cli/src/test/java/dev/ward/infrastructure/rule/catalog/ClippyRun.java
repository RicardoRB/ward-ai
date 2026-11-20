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

  /** Runs Clippy with every lint of {@code ids} (written with {@code _}) on; returns its JSON. */
  static String allLints(
      final String clippy,
      final java.util.Collection<String> ids,
      final Path file
  )
      throws IOException, InterruptedException {
    final Path out = Files.createTempDirectory("ward-clippy-");
    try {
      final List<String> command = new ArrayList<>(
          List.of(
              clippy,
              "--test",
              "--edition=2021",
              "--emit=metadata",
              "--error-format=json",
              "--out-dir=" + out,
              "--cap-lints=warn",
              "-Aclippy::all"
          )
      );
      ids.forEach(id -> command.add("-W" + id));
      command.add(file.toString());
      final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      final String output =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      process.waitFor();
      return output;
    } finally {
      try (Stream<Path> files = Files.walk(out)) {
        files.sorted(Comparator.reverseOrder()).forEach(ClippyRun::delete);
      }
    }
  }

  /** Whether the JSON output holds no error message. */
  static boolean compiled(final String output) {
    return !output.contains("\"level\":\"error\"");
  }

  /** The lines of the primary spans of each lint code in the JSON output of Clippy. */
  static java.util.Map<String, List<Integer>> lines(final String output) {
    final java.util.Map<String, List<Integer>> lines = new java.util.TreeMap<>();
    final Pattern code = Pattern.compile("\"code\":\\{\"code\":\"(clippy::[a-z_0-9]+)\"");
    for (final String message : output.split("\n")) {
      final Matcher lint = code.matcher(message);
      final Matcher span = PRIMARY_SPAN.matcher(message);
      if (lint.find() && span.find()) {
        add(lines, lint.group(1), Integer.parseInt(span.group(LINE_GROUP)));
      }
    }
    return lines;
  }

  private static void add(
      final java.util.Map<String,
      List<Integer>> lines,
      final String lint,
      final int line
  ) {
    lines.computeIfAbsent(lint, key -> new ArrayList<>()).add(line);
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
