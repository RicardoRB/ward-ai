package dev.ward.application.gate.openapi;

import dev.ward.analysis.domain.openapi.ProjectFileReader;
import dev.ward.domain.gate.RevisionFiles;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads project files as they were at one commit, so the {@code $ref}s of the base document resolve
 * at the baseline and never in the working tree. A Git read failure is thrown, not reported as a
 * missing file.
 */
record RevisionFileReader(RevisionFiles revisions, Path root, String commit)
    implements ProjectFileReader {

  @Override
  public Optional<String> read(final String path) {
    return revisions.read(root, commit, path);
  }
}
