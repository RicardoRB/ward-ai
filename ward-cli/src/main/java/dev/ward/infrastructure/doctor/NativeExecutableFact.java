package dev.ward.infrastructure.doctor;

import java.nio.file.Files;
import java.nio.file.Path;

/** Development check: a native build lands in the module, or at the root of a Ward checkout. */
final class NativeExecutableFact {
  private NativeExecutableFact() {}

  static boolean present(final Path base) {
    return Files.isExecutable(base.resolve("target/ward"))
        || Files.isExecutable(base.resolve("ward-cli/target/ward"));
  }
}
