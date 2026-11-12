package dev.ward.application.gate.apicompat;

import dev.ward.domain.gate.RevisionFiles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** An in-memory repository: refs to commits, tags, and files per commit. */
final class FakeRevisions implements RevisionFiles {
  final Map<String, String> refs = new HashMap<>();
  final List<String> reachable = new ArrayList<>();
  final List<String> atHead = new ArrayList<>();
  final Map<String, Map<String, String>> trees = new HashMap<>();
  boolean shallow;

  /** Adds a ref that names {@code commit}. */
  FakeRevisions ref(final String ref, final String commit) {
    refs.put(ref, commit);
    return this;
  }

  /** Adds a tag reachable from HEAD. */
  FakeRevisions tag(final String name) {
    reachable.add(name);
    return ref("refs/tags/" + name, "commit-" + name);
  }

  /** Sets the file {@code path} of {@code commit}. */
  FakeRevisions file(final String commit, final String path, final String text) {
    trees.computeIfAbsent(commit, key -> new TreeMap<>()).put(path, text);
    return this;
  }

  @Override
  public Optional<String> resolve(final Path root, final String revision) {
    return Optional.ofNullable(refs.get(revision));
  }

  @Override
  public Optional<String> mergeBase(final Path root, final String first, final String second) {
    return Optional.empty();
  }

  @Override
  public boolean exists(final Path root, final String commit, final String path) {
    return read(root, commit, path).isPresent();
  }

  @Override
  public List<String> files(final Path root, final String commit, final String directory) {
    final String prefix = directory.isEmpty() ? "" : directory + "/";
    return trees.getOrDefault(commit, Map.of()).keySet().stream()
        .filter(path -> path.startsWith(prefix))
        .map(path -> path.substring(prefix.length()))
        .toList();
  }

  @Override
  public Optional<String> read(final Path root, final String commit, final String path) {
    return Optional.ofNullable(trees.getOrDefault(commit, Map.of()).get(path));
  }

  @Override
  public List<String> tagsReachableFrom(final Path root, final String commit) {
    return List.copyOf(reachable);
  }

  @Override
  public List<String> tagsAt(final Path root, final String commit) {
    return List.copyOf(atHead);
  }

  @Override
  public boolean isShallow(final Path root) {
    return shallow;
  }
}
