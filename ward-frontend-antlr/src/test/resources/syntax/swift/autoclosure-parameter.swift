func fail(with message: @autoclosure () -> String, file: StaticString = #fileID, line: UInt = #line) -> Never {
    fatalError(message(), file: file, line: line)
}
