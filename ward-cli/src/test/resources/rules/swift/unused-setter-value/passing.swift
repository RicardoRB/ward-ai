var aValue: String {
    get {
        return Persister.shared.aValue
    }
    set {
        Persister.shared.aValue = newValue
    }
}
var bValue: String {
    get {
        return Persister.shared.aValue
    }
    set(value) {
        Persister.shared.aValue = value
    }
}
var cached: Int {
    get { 1 }
    set(v) { _ = v }
}
cache.set { }
