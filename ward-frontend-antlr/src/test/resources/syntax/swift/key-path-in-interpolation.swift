struct Shortcuts {
    static var all: [Shortcut] {
        Shortcut(intent: ShowIntent(), phrases: [
            "\(.applicationName) Trends for \(\.$timeframe)"
        ])
    }
}
