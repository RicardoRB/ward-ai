let a = String(decoding: data, as: UTF8.self)
let b = String.init(decoding: data, as: UTF8.self)
let c = String(bytes: data, encoding: .utf8)
