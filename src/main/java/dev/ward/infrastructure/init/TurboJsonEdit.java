package dev.ward.infrastructure.init;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Adds the Turborepo tasks that Ward's gates need to {@code turbo.json}. A task that already exists
 * keeps its settings, and every byte outside the inserted tasks is unchanged.
 */
final class TurboJsonEdit {
  private static final String TASKS = "tasks";

  private TurboJsonEdit() {}

  /**
   * Returns {@code content} with each task of {@code tasks} (name to raw JSON object text) that
   * {@code tasks} does not already define appended to it.
   *
   * @throws dev.ward.domain.config.WardException if the layout cannot be located
   */
  static String addTasks(final String content, final Map<String, String> tasks) {
    final Set<String> existing = JsonMembers.names(content, TASKS);
    final Map<String, String> missing = new LinkedHashMap<>(tasks);
    existing.forEach(missing::remove);
    return missing.isEmpty() ? content : PackageJsonEdit.addMembers(content, TASKS, missing);
  }
}
