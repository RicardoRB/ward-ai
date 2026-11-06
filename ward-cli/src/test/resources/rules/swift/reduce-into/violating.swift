let bar = values.reduce("abc") { $0 + "\($1)" }
let baz = values.reduce([]) { $0 + [$1] }
