package dev.ward.infrastructure.dependency.gradle;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Stands in for Gradle: copies the committed listing into the directory the command names. */
final class FakeGradle implements CommandExecutor {
  final List<List<String>> commands = new ArrayList<>();
  private final Path listing;

  FakeGradle(final Path listing) {
    this.listing = listing;
  }

  @Override
  public CommandResult execute(final List<String> command, final Path workingDirectory) {
    commands.add(command);
    final String option =
        command.stream()
            .filter(a -> a.startsWith("-Dward.dependencies.dir="))
            .findFirst()
            .orElseThrow();
    final Path output = Path.of(option.substring(option.indexOf('=') + 1));
    try (Stream<Path> files = Files.list(listing)) {
      for (final Path file : files.toList()) {
        Files.copy(file, output.resolve(file.getFileName()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new CommandResult(0, "", "", Duration.ZERO);
  }
}
