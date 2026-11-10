package dev.ward.analysis.domain.rule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The text of the manifest files of a project that a project check may read, by project-relative
 * path, and the paths of every other file that exists. The collections are copied on construction,
 * so the check sees a fixed view of the run.
 */
public final class ProjectFiles {

  private final SortedMap<String, String> texts;
  private final Set<String> existing;

  /**
   * Copies the texts into a sorted, unmodifiable map; every text path also exists.
   *
   * @param texts the file texts, by project-relative path with {@code /} separators
   * @throws NullPointerException if the map or one of its paths or texts is missing
   */
  public ProjectFiles(final Map<String, String> texts) {
    this(texts, Set.of());
  }

  /**
   * Copies the texts and the existing paths. A file may exist without its text being read.
   *
   * @param texts the file texts, by project-relative path with {@code /} separators
   * @param existing the paths of the files that exist, with {@code /} separators
   * @throws NullPointerException if a collection or one of its paths or texts is missing
   */
  public ProjectFiles(final Map<String, String> texts, final Set<String> existing) {
    this.texts = copy(texts);
    this.existing = Set.copyOf(Objects.requireNonNull(existing, "existing"));
  }

  /** Returns the view of a project with no manifest files. */
  public static ProjectFiles empty() {
    return new ProjectFiles(Map.of());
  }

  /** Returns the text of {@code path}, or empty when the project has no such file. */
  public Optional<String> text(final String path) {
    return Optional.ofNullable(texts.get(path));
  }

  /** Tells whether the project has a file at {@code path}, whether or not its text is read. */
  public boolean contains(final String path) {
    return texts.containsKey(path) || existing.contains(path);
  }

  /** Returns the paths of every file that exists, read or not, in no particular order. */
  public Set<String> existingPaths() {
    final Set<String> all = new HashSet<>(texts.keySet());
    all.addAll(existing);
    return Collections.unmodifiableSet(all);
  }

  /** Returns the paths of the files, in order. */
  public Set<String> paths() {
    return Collections.unmodifiableSet(texts.keySet());
  }

  private static SortedMap<String, String> copy(final Map<String, String> texts) {
    Objects.requireNonNull(texts, "texts");
    final SortedMap<String, String> copy = new TreeMap<>();
    texts.forEach(
        (path, text) ->
            copy.put(Objects.requireNonNull(path, "path"), Objects.requireNonNull(text, "text")));
    return Collections.unmodifiableSortedMap(copy);
  }
}
