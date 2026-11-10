package dev.ward.infrastructure.config;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads {@code ward.toml} version 2 into the configuration model. */
public class TomlConfigurationReader implements WardConfigurationReader {

  /** The table that holds every check, one sub-table per check name. */
  static final String CHECKS = "checks";

  private static final String VERSION = "version";
  private static final Set<String> TOP_LEVEL_KEYS = Set.of(VERSION, CHECKS);
  private static final Set<String> RENAMED_KEYS = Set.of("quality-gates", "preset");
  private static final String PROFILES = "profiles";
  private static final String MIGRATION =
      " Ward configuration version 2 declares each check as [checks.<name>]; regenerate the file"
          + " with 'ward init --overwrite'.";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public TomlConfigurationReader() {}

  @Override
  public WardConfiguration read(final Path file) {
    if (!Files.isRegularFile(file)) {
      throw new WardException("Configuration file not found: " + file + ". Run 'ward init' first.");
    }
    final TomlParseResult toml;
    try {
      toml = Toml.parse(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
    if (toml.hasErrors()) {
      throw new WardException("Invalid TOML in " + file + ": " + toml.errors().getFirst());
    }
    return parse(toml);
  }

  WardConfiguration parse(final TomlTable toml) {
    checkVersion(toml);
    rejectUnknownKeys(toml);
    return new WardConfiguration(WardConfiguration.SUPPORTED_VERSION, parseChecks(toml));
  }

  private static void checkVersion(final TomlTable toml) {
    final Long version = requireLong(toml, VERSION);
    if (version == null) {
      throw new WardException("Missing required key 'version' in " + WardConfiguration.FILE_NAME);
    }
    if (version != WardConfiguration.SUPPORTED_VERSION) {
      throw new WardException(
          "Unsupported configuration version "
              + version
              + " (supported: "
              + WardConfiguration.SUPPORTED_VERSION
              + ")."
              + MIGRATION);
    }
  }

  private static void rejectUnknownKeys(final TomlTable toml) {
    for (final String key : toml.keySet()) {
      if (PROFILES.equals(key)) {
        throw new WardException(
            "'[profiles]' is no longer supported; declare 'profiles = [...]' under [checks.<name>]"
                + " instead.");
      }
      if (RENAMED_KEYS.contains(key)) {
        throw new WardException("'" + key + "' is no longer supported in ward.toml." + MIGRATION);
      }
      if (!TOP_LEVEL_KEYS.contains(key)) {
        throw new WardException("Unknown top-level key '" + key + "' in ward.toml.");
      }
    }
  }

  private static Map<String, GateConfiguration> parseChecks(final TomlTable toml) {
    final Map<String, GateConfiguration> checks = new LinkedHashMap<>();
    if (!toml.contains(CHECKS)) {
      return checks;
    }
    final TomlTable table = toml.getTable(CHECKS);
    if (table == null) {
      throw new WardException("'" + CHECKS + "' must be a table");
    }
    for (final String name : table.keySet()) {
      checks.put(name, parseCheck(name, table));
    }
    return checks;
  }

  private static GateConfiguration parseCheck(final String name, final TomlTable table) {
    if (name.isBlank()) {
      throw new WardException("Check names under '" + CHECKS + "' must not be blank.");
    }
    final TomlTable check = table.getTable(List.of(name));
    if (check == null) {
      throw new WardException("'" + CHECKS + "." + name + "' must be a table");
    }
    return TomlGateParser.parse(name, check);
  }

  private static Long requireLong(final TomlTable toml, final String key) {
    if (!toml.contains(key)) {
      return null;
    }
    if (!toml.isLong(key)) {
      throw new WardException("'" + key + "' must be an integer");
    }
    return toml.getLong(key);
  }
}
