package dev.ward.domain.init;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reversible rule-file update, including user-owned files that were preserved. {@code warnings}
 * name what init did not apply, such as a preset's rules that a kept user config does not enforce.
 */
public record RuleFileChange(
    Path configDirectory,
    boolean createdDirectory,
    Map<Path, String> originals,
    List<Path> preserved,
    List<String> warnings) {
  /** Copies the originals, preserved files and warnings. */
  public RuleFileChange {
    originals = Collections.unmodifiableMap(new LinkedHashMap<>(originals));
    preserved = List.copyOf(preserved);
    warnings = List.copyOf(warnings);
  }
}
