package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Splits a corpus into shards for the accuracy mode. A shard is a work copy in which only the
 * shard's source files are present, together with the corpus's root-level files that are not
 * sources, such as a build file that Ward reads to detect the project. Nested files that are not
 * sources are left out, and hidden paths such as {@code .git} are never copied.
 */
final class CorpusShard {

  /** Source files per shard: few enough that Ward's JSON never truncates a check's findings. */
  static final int SIZE = 40;

  private static final String DOT = ".";
  private static final String SEPARATOR = "/";

  private CorpusShard() {}

  /**
   * Lists the files of a corpus, relative to its root with forward slashes, skipping hidden paths.
   *
   * @param root the corpus root
   * @return the files, sorted
   */
  static List<String> files(final Path root) {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile)
          .map(root::relativize)
          .filter(relative -> !hidden(relative))
          .map(relative -> relative.toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not list the corpus " + root, e);
    }
  }

  /**
   * Keeps the files with a source extension.
   *
   * @param files the files of a corpus
   * @param extensions the extensions of the source files, with the dot, such as {@code .java}
   * @return the matching files, in order
   */
  static List<String> sourceFiles(final List<String> files, final Set<String> extensions) {
    return files.stream().filter(file -> extensions.contains(extension(file))).toList();
  }

  /** Splits a list into shards of at most {@link #SIZE} files, keeping the order. */
  static List<List<String>> of(final List<String> files) {
    final List<List<String>> shards = new ArrayList<>();
    for (int start = 0; start < files.size(); start += SIZE) {
      shards.add(files.subList(start, Math.min(files.size(), start + SIZE)));
    }
    return List.copyOf(shards);
  }

  /** Returns the extension of a file name, with its dot, or an empty string. */
  static String extension(final String file) {
    final int dot = file.lastIndexOf(DOT);
    final int slash = file.lastIndexOf(SEPARATOR);
    return dot > slash ? file.substring(dot) : "";
  }

  /**
   * Builds the work copy of one shard. A source file is copied only when it belongs to the shard; a
   * root-level file that is not a source is hard-linked when the file system allows it, and copied
   * otherwise.
   *
   * @param root the corpus root
   * @param work the work directory, which is created
   * @param files every file of the corpus, as {@link #files(Path)} lists them
   * @param shard the source files of this shard
   * @param sources every source file of the corpus
   */
  static void populate(
      final Path root,
      final Path work,
      final List<String> files,
      final Set<String> shard,
      final Set<String> sources) {
    try {
      Files.createDirectories(work);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not create the shard directory", e);
    }
    for (final String file : files) {
      if (sources.contains(file)) {
        if (shard.contains(file)) {
          copy(root.resolve(file), work.resolve(file));
        }
      } else if (!file.contains(SEPARATOR)) {
        link(root.resolve(file), work.resolve(file));
      }
    }
  }

  /** Reports whether a path has a hidden segment such as {@code .git}. */
  private static boolean hidden(final Path relative) {
    for (final Path part : relative) {
      if (part.toString().startsWith(DOT)) {
        return true;
      }
    }
    return false;
  }

  private static void link(final Path source, final Path target) {
    try {
      Files.createLink(target, source);
    } catch (IOException | UnsupportedOperationException e) {
      copy(source, target);
    }
  }

  private static void copy(final Path source, final Path target) {
    try {
      Files.createDirectories(Objects.requireNonNull(target.getParent()));
      Files.copy(source, target);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not copy " + source, e);
    }
  }
}
