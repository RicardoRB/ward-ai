package dev.ward.application.doctor;

import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.swift.SwiftToolchainInspection;
import dev.ward.domain.traceability.TraceabilityPrerequisites;

/**
 * The read ports doctor checks the project through, injected as one binding.
 *
 * @param configurationReader reads {@code ward.toml}
 * @param traceabilityPrerequisites checks the traceability gate's inputs
 * @param buildTools reads the runtime JDK and the versions the build file pins
 * @param swift reads the Xcode version and system for Swift projects
 * @param rules reads the rule configuration file, or {@code null} to skip that check
 */
public record DoctorPorts(
    WardConfigurationReader configurationReader,
    TraceabilityPrerequisites traceabilityPrerequisites,
    BuildToolConfiguration buildTools,
    SwiftToolchainInspection swift,
    AnalysisRuleSource rules) {}
