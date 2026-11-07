func run() throws {}

func load() {
    do {
        try run()
    } catch {}
    do {
        try run()
    } catch let error {
        print(error)
    }
}
