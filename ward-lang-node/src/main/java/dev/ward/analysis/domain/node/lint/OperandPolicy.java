package dev.ward.analysis.domain.node.lint;

/**
 * The {@code allow-*} options of restrict-plus-operands: which operand kinds a {@code +} may take.
 *
 * @param allowAny whether an {@code any} operand is allowed
 * @param allowBoolean whether a boolean operand is allowed
 * @param allowNullish whether a {@code null} or {@code undefined} operand is allowed
 * @param allowNumberAndString whether a number and a string may be added
 * @param allowRegExp whether a regular expression operand is allowed
 * @param skipCompound whether the compound assignment {@code +=} is left unchecked
 */
public record OperandPolicy(
    boolean allowAny,
    boolean allowBoolean,
    boolean allowNullish,
    boolean allowNumberAndString,
    boolean allowRegExp,
    boolean skipCompound) {}
