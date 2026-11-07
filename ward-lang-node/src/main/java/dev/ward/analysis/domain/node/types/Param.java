package dev.ward.analysis.domain.node.types;

/**
 * One parameter of a signature.
 *
 * @param name the parameter name
 * @param type the declared type, or {@link TsType#UNKNOWN}
 * @param optional whether the parameter may be omitted
 * @param rest whether it collects the remaining arguments
 */
public record Param(String name, TsType type, boolean optional, boolean rest) {}
