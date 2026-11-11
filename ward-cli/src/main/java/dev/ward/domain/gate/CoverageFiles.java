package dev.ward.domain.gate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Outbound port for the file facts that the {@code crap} gate needs about reports and sources. */
public interface CoverageFiles {

  /** Returns {@code root} and the module directories below it, where a build writes reports. */
  List<Path> moduleDirectories(Path root);

  /** Returns whether {@code file} is a regular file. */
  boolean isFile(Path file);

  /**
   * Returns when {@code file} was last modified, or {@link Instant#MIN} when that cannot be read.
   */
  Instant modified(Path file);
}
