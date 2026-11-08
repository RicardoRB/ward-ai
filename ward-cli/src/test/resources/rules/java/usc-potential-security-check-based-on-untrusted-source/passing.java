// padding 0 keeps the call positions apart from violating.java
// padding 1 keeps the call positions apart from violating.java
// padding 2 keeps the call positions apart from violating.java
// padding 3 keeps the call positions apart from violating.java
// padding 4 keeps the call positions apart from violating.java
// padding 5 keeps the call positions apart from violating.java
// padding 6 keeps the call positions apart from violating.java
// padding 7 keeps the call positions apart from violating.java
// padding 8 keeps the call positions apart from violating.java
// padding 9 keeps the call positions apart from violating.java
// padding 10 keeps the call positions apart from violating.java
// padding 11 keeps the call positions apart from violating.java
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class Reader {
  public String finalMethod(File file) {
    if (!file.getName().isEmpty()) {
      return AccessController.doPrivileged((PrivilegedAction<String>) () -> file.getName());
    }
    return null;
  }

  public String other(File file) {
    if (!file.getName().isEmpty()) {
      return AccessController.doPrivileged((PrivilegedAction<String>) () -> file.getPath());
    }
    return null;
  }

  String notPublic(File file) {
    if (!file.getName().isEmpty()) {
      return AccessController.doPrivileged((PrivilegedAction<String>) () -> file.getName());
    }
    return null;
  }
}
