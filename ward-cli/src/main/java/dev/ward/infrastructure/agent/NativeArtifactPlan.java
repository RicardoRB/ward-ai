package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.IntegrationCapability;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adapter-provided content plan consumed by shared ownership-safe persistence. A harness without a
 * wrapper script has a {@code null} {@code hookPath} and {@code hookContent}.
 */
public record NativeArtifactPlan(
    String id,
    Path configPath,
    Path hookPath,
    String marker,
    String configContent,
    String hookContent,
    Set<IntegrationCapability> capabilities,
    List<Path> legacyPaths) {
  /** Copies the capabilities and legacy paths. */
  public NativeArtifactPlan {
    capabilities = Set.copyOf(capabilities);
    legacyPaths = List.copyOf(legacyPaths);
  }

  /** Whether the plan has a wrapper script. */
  boolean hasHook() {
    return hookPath != null;
  }

  /** The files the integration writes and owns, in write order. */
  List<Path> files() {
    return hasHook() ? List.of(configPath, hookPath) : List.of(configPath);
  }

  /** The earlier-version files that carry this integration's marker. */
  LegacyFiles legacy(final Path legacyPath) {
    return new LegacyFiles(
        legacyPath.getParent(),
        Pattern.compile(Pattern.quote(legacyPath.getFileName().toString())),
        Pattern.compile(Pattern.quote(marker)));
  }
}
