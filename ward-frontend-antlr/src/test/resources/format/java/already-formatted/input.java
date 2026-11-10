package demo;

import java.util.List;

/**
 * A formatted class.
 */
public class Ready {
    private final List<String> names;

    public Ready(List<String> names) {
        this.names = names;
    }

    String first() {
        if (names.isEmpty()) {
            return "";
        }
        return names.get(0);
    }
}
