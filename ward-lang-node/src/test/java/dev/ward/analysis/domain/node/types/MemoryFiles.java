package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/** Declaration files kept in memory, counting the reads. */
final class MemoryFiles implements DeclarationFiles {
  private final Map<String, String> files = new HashMap<>();
  private final AtomicInteger reads = new AtomicInteger();

  MemoryFiles with(final String path, final String text) {
    files.put(path, text);
    return this;
  }

  int reads() {
    return reads.get();
  }

  List<String> paths() {
    return new ArrayList<>(new TreeSet<>(files.keySet()));
  }

  @Override
  public Optional<String> read(final String path) {
    reads.incrementAndGet();
    return Optional.ofNullable(files.get(path));
  }

  @Override
  public boolean exists(final String path) {
    return files.containsKey(path);
  }

  @Override
  public List<String> list(final String directory) {
    final String prefix = directory + "/";
    return files.keySet().stream()
        .filter(path -> path.startsWith(prefix))
        .map(path -> path.substring(prefix.length()).split("/")[0])
        .distinct()
        .toList();
  }

  @Override
  public Optional<String> packageHash(final String directory) {
    final String prefix = directory + "/";
    final String content =
        files.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + entry.getValue())
            .reduce("", String::concat);
    return content.isEmpty()
        ? Optional.empty()
        : Optional.of(dev.ward.analysis.domain.types.CachedTypeFacts.sha256(content));
  }
}
