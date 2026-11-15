import java.util.Enumeration;

final class Legacy implements Enumeration<String> {
  public boolean hasMoreElements() {
    return false;
  }

  public String nextElement() {
    return "";
  }
}
