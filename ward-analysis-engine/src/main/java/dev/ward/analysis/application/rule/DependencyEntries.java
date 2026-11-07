package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.port.DependencySupport;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Collects the manifest entry files that every dependency support declares. */
final class DependencyEntries {
  private DependencyEntries() {}

  /** Returns the sorted entry files of all {@code dependencies}; empty when none declare any. */
  static Set<String> of(final List<DependencySupport> dependencies) {
    final Set<String> entries = new TreeSet<>();
    dependencies.forEach(support -> entries.addAll(support.resolver().entryFiles()));
    return entries;
  }
}
