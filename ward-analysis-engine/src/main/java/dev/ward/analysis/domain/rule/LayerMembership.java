package dev.ward.analysis.domain.rule;

/** Whether a file is in a layer: a directory of its path has the layer's name. */
final class LayerMembership {

  private LayerMembership() {}

  /** A directory of {@code path} has the layer's name; Swift folders compare ignoring case. */
  static boolean inLayer(final String path, final String layer) {
    final boolean fold = path.endsWith(".swift");
    final String[] segments = path.split("/");
    for (int index = 0; index < segments.length - 1; index++) {
      if (fold ? segments[index].equalsIgnoreCase(layer) : segments[index].equals(layer)) {
        return true;
      }
    }
    return false;
  }
}
