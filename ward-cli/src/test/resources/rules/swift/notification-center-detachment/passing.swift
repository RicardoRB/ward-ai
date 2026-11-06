class Watcher {
    deinit {
        center.removeObserver(self)
    }
}
