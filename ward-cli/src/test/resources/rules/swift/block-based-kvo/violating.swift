class Watcher {
    func start() { self.addObserver(self, forKeyPath: "name", options: [], context: nil) }
    override func observeValue(forKeyPath keyPath: String?) {}
}
