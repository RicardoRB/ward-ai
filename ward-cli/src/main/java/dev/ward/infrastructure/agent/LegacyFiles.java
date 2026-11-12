package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ward-owned files that an earlier version wrote and the current version no longer does. A file is
 * Ward-owned when its name matches {@code name} and its content matches {@code marker}; a file with
 * the same name and other content belongs to the user and is left alone.
 *
 * @param directory the directory that held the files
 * @param name the pattern the whole file name matches
 * @param marker the pattern of the ownership marker inside the file
 */
record LegacyFiles(Path directory, Pattern name, Pattern marker) {

  /** The files that Ward owns today, in name order. */
  List<Path> owned() throws IOException {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    final List<Path> candidates;
    try (Stream<Path> entries = Files.list(directory)) {
      candidates =
          entries
              .filter(Files::isRegularFile)
              .filter(file -> name.matcher(String.valueOf(file.getFileName())).matches())
              .sorted()
              .toList();
    }
    final List<Path> owned = new ArrayList<>();
    for (final Path candidate : candidates) {
      if (marker.matcher(Files.readString(candidate)).find()) {
        owned.add(candidate);
      }
    }
    return owned;
  }

  /** The removals a refresh or removal of the integration would make. */
  List<PlannedFileChange> plan() throws IOException {
    return owned().stream()
        .map(file -> new PlannedFileChange(file, PlannedFileChange.Action.REMOVE))
        .toList();
  }

  /** Deletes the owned files and returns them. */
  List<Path> delete() throws IOException {
    final List<Path> owned = owned();
    for (final Path file : owned) {
      Files.delete(file);
    }
    return owned;
  }
}
