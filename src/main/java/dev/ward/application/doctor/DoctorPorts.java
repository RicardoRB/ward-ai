package dev.ward.application.doctor;

import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.traceability.TraceabilityPrerequisites;

/**
 * The read ports doctor checks the project through, injected as one binding.
 *
 * @param configurationReader reads {@code ward.toml}
 * @param traceabilityPrerequisites checks the traceability gate's inputs
 * @param buildTools reads the runtime JDK and the versions the build file pins
 */
public record DoctorPorts(
    WardConfigurationReader configurationReader,
    TraceabilityPrerequisites traceabilityPrerequisites,
    BuildToolConfiguration buildTools) {}
