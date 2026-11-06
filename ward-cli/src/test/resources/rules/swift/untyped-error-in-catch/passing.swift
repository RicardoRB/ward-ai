do {
    try run()
} catch let error as MyError {
    print(error)
}
