class Rules {
    static final Set<String> IDS =
        Set.of(
            // the first reason
            "first",
            // the second reason
            "second");

    void send(Builder builder) {
        builder.name( // the reason beside the parenthesis
            "first" // the reason beside the item
        );
        builder.name( /* a block reason */ "second", "a long item that makes this list break into lines", 3);
    }
}
