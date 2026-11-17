package dev.ward.domain.init;

import java.util.List;

/**
 * Validated choices made by an initialization request. {@code ci} names the CI provider whose
 * workflow init writes, or is {@code null} for none; {@code agentRules} writes the gate rules into
 * {@code AGENTS.md}; {@code architectureChosen} tells that the user named the architecture style,
 * so init also writes a {@code ward-architecture.toml} for it; {@code promptHook} also registers the
 * agent hook that scans prompts for secrets, for the integrations that have one.
 */
public record InitSelection(
    List<String> integrations,
    List<String> gates,
    String architecture,
    QualityPreset preset,
    List<String> installTools,
    boolean qualityHooks,
    String ci,
    boolean agentRules,
    String scheme,
    boolean architectureChosen,
    boolean promptHook
) {
  /** Copies the lists; missing optional lists mean empty. */
  public InitSelection {
    integrations = List.copyOf(integrations);
    gates = List.copyOf(gates);
    installTools = installTools == null ? List.of() : List.copyOf(installTools);
  }

  /** Returns this selection with the prompt hook asked for or not. */
  public InitSelection withPromptHook(final boolean enabled) {
    return new InitSelection(
        integrations,
        gates,
        architecture,
        preset,
        installTools,
        qualityHooks,
        ci,
        agentRules,
        scheme,
        architectureChosen,
        enabled
    );
  }

  /** Selects one integration and one gate. */
  public InitSelection(final String integration, final String gate) {
    this(
        List.of(integration),
        List.of(gate),
        null,
        null,
        List.of(),
        false,
        null,
        false,
        null,
        false,
        false
    );
  }

  /** Selects {@code integrations} and one gate. */
  public InitSelection(final List<String> integrations, final String gate) {
    this(
        integrations,
        List.of(gate),
        null,
        null,
        List.of(),
        false,
        null,
        false,
        null,
        false,
        false
    );
  }

  /** Selects {@code integrations} and {@code gates}, without preset, installs, or hooks. */
  public InitSelection(final List<String> integrations, final List<String> gates) {
    this(integrations, gates, null, null, List.of(), false, null, false, null, false, false);
  }

  /** Selects {@code integrations} and {@code gates} with an architecture style. */
  public InitSelection(
      final List<String> integrations,
      final List<String> gates,
      final String architecture
  ) {
    this(
        integrations,
        gates,
        architecture,
        null,
        List.of(),
        false,
        null,
        false,
        null,
        false,
        false
    );
  }

  /** Selects {@code integrations} and {@code gates} with an architecture style and preset. */
  public InitSelection(
      final List<String> integrations,
      final List<String> gates,
      final String architecture,
      final QualityPreset preset
  ) {
    this(
        integrations,
        gates,
        architecture,
        preset,
        List.of(),
        false,
        null,
        false,
        null,
        false,
        false
    );
  }
}
