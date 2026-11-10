package example;

import java.util.List;

public class Unnamed {

    int count(List<Object> items) {
        int total = 0;
        for (Object _ : items) {
            total++;
        }
        try {
            Integer.parseInt("x");
        } catch (NumberFormatException _) {
            total--;
        }
        if (items instanceof List<?> _) {
            total++;
        }
        items.forEach(_ -> total++);
        return total;
    }
}
