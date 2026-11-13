package dev.ward.domain.init;

import java.util.Map;

/** Opinionated, immutable quality configurations used by {@code ward init}. */
public enum QualityPreset {
  STANDARD("standard", new PresetRules(Map.of("LINE", 0.70), null, false)),
  STRICT("strict", new PresetRules(Map.of("LINE", 0.85, "BRANCH", 0.75), 60, true));

  private final String id;

  // Enum constants serialize by name, so their fields are never serialized.
  private final transient PresetRules rules;

  QualityPreset(final String id, final PresetRules rules) {
    this.id = id;
    this.rules = rules;
  }

  /** Returns the preset ID used in {@code ward.toml} and on the command line. */
  public String id() {
    return id;
  }

  /** Returns the tool rules the preset writes. */
  public PresetRules rules() {
    return rules;
  }

  /** The tool rules a preset writes into rule files and the build. */
  public record PresetRules(
      Map<String, Double> jacocoMinimums, Integer mutationThreshold, boolean architectureCycles) {
    /** Copies the collections. */
    public PresetRules {
      jacocoMinimums = Map.copyOf(jacocoMinimums);
    }
  }
}
