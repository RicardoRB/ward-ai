class Answering {
    /** Asks. */
    <T> T ask(final Supplier<T> question) {
        return question.get();
    }
}
