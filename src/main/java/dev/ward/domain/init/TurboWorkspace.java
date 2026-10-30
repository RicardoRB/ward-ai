package dev.ward.domain.init;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The part of a Turborepo workspace that init writes: the root that holds {@code turbo.json}, the
 * members that get scripts, and the turbo tasks the root's gates run.
 *
 * @param root the workspace root directory
 * @param members the members that get package.json scripts
 * @param tasks the turbo task names the root's gates run, such as {@code lint}
 */
public record TurboWorkspace(Path root, List<Member> members, List<String> tasks) {
  /** Copies the lists. */
  public TurboWorkspace {
    members = List.copyOf(members);
    tasks = List.copyOf(tasks);
  }

  /**
   * A workspace member and the commands it gets as scripts, keyed by the script name, which is the
   * turbo task that runs it.
   *
   * @param name the member's package name, for messages
   * @param directory the member directory
   * @param scripts the command text of each script, by script name
   */
  public record Member(String name, Path directory, Map<String, String> scripts) {
    /** Copies the scripts. */
    public Member {
      scripts = Map.copyOf(scripts);
    }
  }
}
