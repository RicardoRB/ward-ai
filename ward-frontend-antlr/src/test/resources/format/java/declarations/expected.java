package demo.model;

import java.util.List;
import java.util.Map;

public class Account extends Base implements Auditable, Serializable {
    private final String id;
    private static int count = 0;
    public Account(String id) {
        this.id = id;
    }
    public String id() {
        return id;
    }
}

interface Auditable {
    String describe();
    default boolean active() {
        return true;
    }
}

enum Status {
    OPEN,
    CLOSED,
    SUSPENDED
}

record Point(int x, int y) {
    public Point {
        if (x < 0) throw new IllegalArgumentException("negative");
    }
}
