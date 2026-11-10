func show(name: String?) {
  if let name {
    print(name)
  }
  guard let name else { return }
  print(name)
}
