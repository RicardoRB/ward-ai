package dev.ward.analysis.domain.rule.check;

/**
 * The boolean options of {@link NodeMagicNumberCheck}, with the defaults of ESLint's
 * no-magic-numbers.
 *
 * @param detectObjects whether a number that is a property value or an assignment target of an
 *     object is reported
 * @param ignoreArrayIndexes whether a number that indexes an array is left alone
 * @param ignoreDefaultValues whether a default value of a parameter or pattern is left alone
 * @param ignoreClassFieldInitialValues whether the initial value of a class field is left alone
 * @param enforceConst whether a number declared with {@code let} or {@code var} is reported
 */
public record NodeMagicNumberFlags(
    boolean detectObjects,
    boolean ignoreArrayIndexes,
    boolean ignoreDefaultValues,
    boolean ignoreClassFieldInitialValues,
    boolean enforceConst) {}
