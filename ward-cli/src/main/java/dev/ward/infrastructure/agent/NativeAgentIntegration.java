package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.IntegrationCapability;
import java.util.List;
import java.util.Set;

/** Metadata shared by concrete native harness adapters. */
public interface NativeAgentIntegration {

  /**
   * Describes a native harness: its id, generated files, hook events and capabilities. {@code
   * hookFile} is empty when the harness needs only its configuration file. {@code legacyFiles} name
   * files that an earlier Ward version wrote and a refresh or removal deletes while they carry the
   * Ward ownership marker.
   */
  record Definition(
      String id,
      String configFile,
      String hookFile,
      String events,
      Set<IntegrationCapability> capabilities,
      List<String> legacyFiles) {
    /** Copies the capabilities and legacy files. */
    public Definition {
      capabilities = Set.copyOf(capabilities);
      legacyFiles = List.copyOf(legacyFiles);
    }

    /** Creates a definition without legacy files. */
    public Definition(
        final String id,
        final String configFile,
        final String hookFile,
        final String events,
        final Set<IntegrationCapability> capabilities) {
      this(id, configFile, hookFile, events, capabilities, List.of());
    }

    /** Whether the harness has a wrapper script besides its configuration file. */
    public boolean hasHookFile() {
      return !hookFile.isEmpty();
    }
  }
}
