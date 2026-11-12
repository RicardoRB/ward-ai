package dev.ward.domain.gate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Outbound Git port for gates that compare a file with an earlier revision. */
public interface RevisionFiles {
  /** Returns the commit ID {@code revision} names in {@code root}, or empty when it names none. */
  Optional<String> resolve(Path root, String revision);

  /** Returns the merge base of {@code first} and {@code second}, or empty when there is none. */
  Optional<String> mergeBase(Path root, String first, String second);

  /** Returns whether {@code path}, relative to {@code root}, exists at commit {@code commit}. */
  boolean exists(Path root, String commit, String path);

  /**
   * Returns the files below {@code directory}, relative to {@code root}, at commit {@code commit},
   * as paths relative to that directory; empty when the directory does not exist there. An empty
   * {@code directory} names {@code root}.
   */
  List<String> files(Path root, String commit, String directory);

  /**
   * Returns the text of {@code path}, relative to {@code root}, at {@code commit}, if it exists.
   */
  Optional<String> read(Path root, String commit, String path);

  /** Returns the names of the tags reachable from {@code commit}, in no particular order. */
  List<String> tagsReachableFrom(Path root, String commit);

  /** Returns the names of the tags that point at {@code commit}. */
  List<String> tagsAt(Path root, String commit);

  /** Returns whether the repository of {@code root} is a shallow clone. */
  boolean isShallow(Path root);
}
