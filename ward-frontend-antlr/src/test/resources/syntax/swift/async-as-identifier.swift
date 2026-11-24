struct Hasher2 {
  var async: Int { 1 }
  func async(_ work: () -> Void) {}
  func run() {
    setup(asyncEnvironment: async)
    configure(async: false, logger: nil)
  }
}
let async = true
