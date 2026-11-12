package dev.ward.application.gate;

import dev.ward.application.gate.apicompat.ApiCompatGates;
import dev.ward.application.gate.buildpolicy.BuildPolicyGates;
import dev.ward.application.license.LicenseService;

/**
 * The optional dependencies of the in-process gates that read the build files or the dependency
 * inventory against a policy.
 *
 * @param license looks up the licenses of the {@code license} gate, or {@code null}
 * @param buildPolicy builds the {@code build-policy} gate, or {@code null}
 * @param apiCompat builds the native {@code api-compat} gate, or {@code null}
 */
public record PolicyGatePorts(
    LicenseService license, BuildPolicyGates buildPolicy, ApiCompatGates apiCompat) {}
