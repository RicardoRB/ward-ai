package dev.ward.application.init;

import dev.ward.domain.project.BuildTool;
import java.util.List;
import java.util.stream.Stream;

/**
 * Turborepo: {@code turbo run <task>} with flags that keep its output readable per package. The
 * per-package output is read by {@code TurboOutput}.
 */
final class TurboTaskRunner implements TaskRunnerStrategy {
  private static final List<String> FLAGS =
      List.of("--log-prefix=task", "--log-order=grouped", "--continue=always", "--ui=stream");

  @Override
  public List<String> rootTaskCommand(final BuildTool manager, final String task) {
    final String[] arguments =
        Stream.concat(Stream.of("turbo", "run", task), FLAGS.stream()).toArray(String[]::new);
    return NodeTools.command(manager, arguments);
  }

  @Override
  public String packageFilter() {
    return "--filter=...{package}";
  }
}
