import android.os.Bundle;
import java.util.LinkedHashMap;

class Reader {
  Object read(Bundle bundle) {
    return (LinkedHashMap<String, String>) bundle.getSerializable("k");
  }
}
