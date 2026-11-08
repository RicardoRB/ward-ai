import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class Reader {
  public String read(File file) {
    if (!file.getName().isEmpty()) {
      return AccessController.doPrivileged((PrivilegedAction<String>) () -> file.getName());
    }
    return null;
  }
}
