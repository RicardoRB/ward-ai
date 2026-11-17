package dev.ward.config;

import dev.ward.application.gate.AnalysisResultCodec;
import dev.ward.infrastructure.analysis.jvm.JdkLocator;

/**
 * The collaborators of the analysis wiring that are not rules or sources.
 *
 * @param codec reads and writes cached results
 * @param jdks finds the JDK that Java and Kotlin types are read from
 */
record AnalysisServices(AnalysisResultCodec codec, JdkLocator jdks) {}
