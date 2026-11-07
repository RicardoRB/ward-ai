func load() {
    do {
        try run()
    } catch let Failure {
        print(Failure)
    }
}

func run() throws {}
