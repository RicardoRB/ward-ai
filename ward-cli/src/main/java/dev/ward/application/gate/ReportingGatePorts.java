package dev.ward.application.gate;

/**
 * The optional dependencies of the in-process gates that read a report or the dependency inventory.
 *
 * @param secrets builds the {@code secrets} gate, or {@code null}
 * @param crap gives the {@code crap} gate its report readers, or {@code null}
 * @param vulnerabilities builds the {@code vulnerabilities} gate, or {@code null}
 */
public record ReportingGatePorts(
    SecretGates secrets, CrapPorts crap, VulnerabilityGates vulnerabilities) {}
