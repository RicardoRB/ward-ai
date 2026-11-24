package dev.ward.analysis.domain.python;

import java.util.Map;
import java.util.Set;

/**
 * What the whole file says that a body needs: its imports, the collections it fixes, the {@code
 * module#} prefix of its body names (empty when the path is not a module name) and the functions
 * it defines at its top level. A body named {@code a.b#f} is the function {@code f} that a call
 * through module {@code a.b} reaches.
 *
 * @param imports what the names bound by the imports of the file stand for
 * @param collections the names that the file binds once, to a collection the program fixes
 * @param owner the {@code module#} prefix of the body names, or empty
 * @param functions the names of the functions defined at the top level of the file
 */
record PythonFileFacts(
    Map<String,
    String> imports,
    Set<String> collections,
    String owner,
    Set<String> functions
) {
  /** The dotted name of the module, or empty when the file has none. */
  String module() {
    return owner.isEmpty() ? "" : owner.substring(0, owner.length() - 1);
  }

  /** The facts for a body that cannot use the given names as fixed collections. */
  PythonFileFacts with(final Set<String> usable) {
    return new PythonFileFacts(imports, usable, owner, functions);
  }
}
