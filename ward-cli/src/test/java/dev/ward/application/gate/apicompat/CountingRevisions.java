package dev.ward.application.gate.apicompat;

import dev.ward.domain.gate.RevisionSnapshot;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A fake repository that counts the snapshots opened and closed. */
final class CountingRevisions extends FakeRevisions {
  int opened;
  int closed;

  @Override
  public RevisionSnapshot open(final Path root, final String commit) {
    opened++;
    final RevisionSnapshot inner = super.open(root, commit);
    return new RevisionSnapshot() {
      @Override
      public Optional<String> read(final String path) {
        return inner.read(path);
      }

      @Override
      public List<String> files(final String directory) {
        return inner.files(directory);
      }

      @Override
      public void close() {
        closed++;
      }
    };
  }
}
