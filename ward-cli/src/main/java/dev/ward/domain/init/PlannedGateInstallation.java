package dev.ward.domain.init;

/**
 * A missing gate executable and where to read how to install it.
 *
 * @param tool the executable the gate runs
 * @param guidance a link to the installation instructions
 */
public record PlannedGateInstallation(String tool, String guidance) {}
