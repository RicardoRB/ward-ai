import java.util.Enumeration;
import java.util.Iterator;

final class Legacy {
  void visit(Iterator<String> names, Enumeration<String> roots) {
    while (roots.hasMoreElements()) {
      roots.nextElement();
    }
  }
}
