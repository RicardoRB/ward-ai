func load() {
    do {
        try run()
    } catch let error {
        print(error)
    }
}

func run() throws {}

func open() {
    do {
        try run()
    } catch LoadError.unknown(let result) {
        print(result)
    }
}
