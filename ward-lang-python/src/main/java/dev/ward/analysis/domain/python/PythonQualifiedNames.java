package dev.ward.analysis.domain.python;

/** Splits and joins the dotted names of stub modules and the classes and functions they declare. */
final class PythonQualifiedNames {

  /** The module that holds the built-in classes and functions. */
  static final String BUILTINS = "builtins";

  private static final String DOT = ".";

  private PythonQualifiedNames() {}

  /** The qualified name of {@code name} declared in {@code module}; built-ins stay unqualified. */
  static String qualify(final String module, final String name) {
    return BUILTINS.equals(module) ? name : module + DOT + name;
  }

  /** The module of a qualified name; a name with no dot is a built-in. */
  static String moduleOf(final String qualified) {
    final int dot = qualified.lastIndexOf('.');
    return dot < 0 ? BUILTINS : qualified.substring(0, dot);
  }

  /** The last part of a qualified name. */
  static String simpleName(final String qualified) {
    final int dot = qualified.lastIndexOf('.');
    return dot < 0 ? qualified : qualified.substring(dot + 1);
  }
}
