package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.BuildToolConfiguration;

/**
 * The read ports the init catalog needs, injected as one binding.
 *
 * @param environment resolves which executables are on the path
 * @param node reads Node packages
 * @param buildTools reads the runtime JDK and the gates it cannot run
 */
public record InitPorts(
    EnvironmentInspection environment, NodeInputs node, BuildToolConfiguration buildTools) {}
