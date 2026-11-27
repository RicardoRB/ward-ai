package dev.ward.analysis.domain.node;

import java.util.Optional;

/** Names the owner of the methods of an object that another file of the project exports. */
final class ObjectOwners {
  private ObjectOwners() {}

  /**
   * The owner of the methods of an object: {@code module#object} for {@code ns.object}, where
   * {@code ns} is bound to a relative module as a whole.
   *
   * @param imports the imports of the file
   * @param written a receiver as written, such as {@code security.authenticatedUsers}
   * @return the owner, or empty when the receiver is not a member of such a module
   */
  static Optional<String> of(final NodeImports imports, final String written) {
    final int dot = written.indexOf('.');
    if (dot < 0 || written.indexOf('.', dot + 1) >= 0) {
      return Optional.empty();
    }
    final String local = written.substring(0, dot);
    return imports.isProjectModule(local)
        ? imports.moduleOf(local).map(module -> module + "#" + written.substring(dot + 1))
        : Optional.empty();
  }
}
