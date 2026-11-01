package dev.ward.infrastructure.project;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.SwiftToolchainInspection;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Reads the selected Xcode version by running {@code xcodebuild -version}. */
public final class ProcessSwiftToolchainInspection implements SwiftToolchainInspection {
  private static final Pattern XCODE_VERSION = Pattern.compile("\\AXcode\\s+(\\d[^\\s]*)");
  private static final Logger LOGGER =
      Logger.getLogger(ProcessSwiftToolchainInspection.class.getName());
  private final CommandExecutor commands;
  private final String osName;

  /** Creates the inspection; {@code commands} runs {@code xcodebuild}. */
  @Inject
  public ProcessSwiftToolchainInspection(final CommandExecutor commands) {
    this(commands, System.getProperty("os.name", ""));
  }

  /** Creates the inspection for a machine whose {@code os.name} is {@code osName}. */
  ProcessSwiftToolchainInspection(final CommandExecutor commands, final String osName) {
    this.commands = commands;
    this.osName = osName;
  }

  @Override
  public Optional<String> xcodeVersion() {
    if (!macOs()) {
      return Optional.empty();
    }
    final CommandResult result =
        commands.execute(List.of("xcodebuild", "-version"), Path.of("").toAbsolutePath());
    LOGGER.fine(() -> "xcodebuild -version exited with " + result.exitCode());
    if (result.hasExecutionError() || !result.succeeded()) {
      return Optional.empty();
    }
    final Matcher matcher = XCODE_VERSION.matcher(result.stdout().strip());
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  @Override
  public boolean macOs() {
    return osName.toLowerCase(Locale.ROOT).startsWith("mac");
  }
}
