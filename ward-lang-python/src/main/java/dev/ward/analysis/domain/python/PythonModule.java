package dev.ward.analysis.domain.python;

/**
 * A module of a Python project.
 *
 * @param name the dotted module name, such as {@code shop.price}
 * @param pkg whether the file is a package's {@code __init__.py}, which is its own package
 */
record PythonModule(String name, boolean pkg) {}
