package dev.ward.domain.project;

import java.nio.file.Path;

/** Outbound contract for asking whether a project-relative path is a directory or a file. */
public interface ProjectPaths {

  /** Returns whether the project-relative path names an existing directory. */
  boolean isDirectory(Path projectRoot, Path relative);

  /** Returns whether the project-relative path names an existing regular file. */
  boolean isRegularFile(Path projectRoot, Path relative);
}
