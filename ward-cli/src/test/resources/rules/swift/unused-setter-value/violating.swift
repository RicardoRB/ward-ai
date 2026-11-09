var aValue: String {
    get {
        return Persister.shared.aValue
    }
    set {
        Persister.shared.aValue = aValue
    }
}
var bValue: String {
    get {
        return Persister.shared.aValue
    }
    set(value) {
        Persister.shared.aValue = aValue
    }
}
