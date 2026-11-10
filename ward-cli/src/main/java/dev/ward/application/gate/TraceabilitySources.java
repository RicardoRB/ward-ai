package dev.ward.application.gate;

import dev.ward.application.gate.openspec.OpenSpecJunitTraceabilityGate;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.traceability.ScenarioInventory;
import dev.ward.domain.traceability.TestMappingInventory;

/**
 * The inventories the traceability gate reads.
 *
 * @param scenarios the live OpenSpec scenarios
 * @param mappings the JUnit mappings to those scenarios
 */
public record TraceabilitySources(ScenarioInventory scenarios, TestMappingInventory mappings) {
  /**
   * Builds the traceability gate over these inventories.
   *
   * @return the gate
   * @throws WardException if an inventory is missing
   */
  public QualityGate gate() {
    if (scenarios == null || mappings == null) {
      throw new WardException("Traceability gate dependencies are unavailable.");
    }
    return new OpenSpecJunitTraceabilityGate(scenarios, mappings);
  }
}
