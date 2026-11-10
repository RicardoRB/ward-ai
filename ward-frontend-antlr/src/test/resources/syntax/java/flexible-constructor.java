package example;

public class Child extends Base {

    public Child(int value) {
        int checked = validate(value);
        super(checked);
    }

    private static int validate(int value) {
        return Math.max(value, 0);
    }
}
