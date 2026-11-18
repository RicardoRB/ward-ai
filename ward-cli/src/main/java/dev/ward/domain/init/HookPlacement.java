package dev.ward.domain.init;

import java.nio.file.Path;

/**
 * Where init writes the quality hooks: the directory Git runs, or a folder tracked in the
 * repository that every clone shares.
 *
 * @param sharedFolder the repository-relative shared folder, or {@code null} for the directory Git
 *     runs
 * @param setHooksPath whether init also sets {@code core.hooksPath} to the shared folder for this
 *     clone
 */
public record HookPlacement(String sharedFolder, boolean setHooksPath) {
  /** The folder shared hooks use unless the user names another. */
  public static final String DEFAULT_FOLDER = ".githooks";

  /** The default: the hooks directory Git runs, with no change to {@code core.hooksPath}. */
  public static final HookPlacement GIT_RUNS = new HookPlacement(null, false);

  /** Rejects a folder that is absolute, leaves the repository, or is inside {@code .git}. */
  public HookPlacement {
    if (sharedFolder != null) {
      final Path folder = Path.of(sharedFolder).normalize();
      if (sharedFolder.isBlank()
          || folder.isAbsolute()
          || folder.startsWith("..")
          || folder.startsWith(".git")
          || folder.toString().isEmpty()) {
        throw new IllegalArgumentException(
            "The shared hooks folder must be a repository-relative folder outside .git: "
                + sharedFolder
        );
      }
      sharedFolder = folder.toString();
    }
  }

  /** Shared hooks in {@code folder}, optionally pointing this clone's {@code core.hooksPath}. */
  public static HookPlacement shared(final String folder, final boolean setHooksPath) {
    return new HookPlacement(folder, setHooksPath);
  }

  /** Returns whether the hooks go to a tracked folder. */
  public boolean isShared() {
    return sharedFolder != null;
  }

  /** The command a teammate runs once to use the shared folder. */
  public String teammateCommand() {
    return "git config core.hooksPath " + sharedFolder;
  }
}
