func destination(in directory: Directory = .documents) -> Destination {
    { temporaryURL, response in
        let urls = FileManager.default.urls(for: directory)
        return (urls.first ?? temporaryURL, [])
    }
}
