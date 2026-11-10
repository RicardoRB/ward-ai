class Text {
    String message = "hello   world  with   spaces";
    String block = """
    keep   these   spaces
      and this indent
    """;
    void run() {
        log("a  b", 'c', "tab\there");
    }
}
