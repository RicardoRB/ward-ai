// padding 0 keeps the positions apart from violating.java
// padding 1 keeps the positions apart from violating.java
// padding 2 keeps the positions apart from violating.java
// padding 3 keeps the positions apart from violating.java
// padding 4 keeps the positions apart from violating.java
// padding 5 keeps the positions apart from violating.java
// padding 6 keeps the positions apart from violating.java
// padding 7 keeps the positions apart from violating.java
// padding 8 keeps the positions apart from violating.java
// padding 9 keeps the positions apart from violating.java
// padding 10 keeps the positions apart from violating.java
// padding 11 keeps the positions apart from violating.java
import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;

class Reader {
  Object exactMap(Bundle bundle) {
    return (HashMap<String, String>) bundle.getSerializable("k");
  }

  Object baseMap(Bundle bundle) {
    return (Map<String, String>) bundle.getSerializable("k");
  }

  Object text(Bundle bundle) {
    return (String) bundle.getSerializable("k");
  }

  Object unknownHierarchy(Bundle bundle) {
    return (Mystery) bundle.getSerializable("k");
  }

  Object notBundle(Holder holder) {
    return (java.util.TreeMap<String, String>) holder.getSerializable("k");
  }
}
