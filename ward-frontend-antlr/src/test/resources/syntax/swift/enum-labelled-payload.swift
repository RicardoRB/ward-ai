enum Address {
  case host(_ host: String?, port: Int?)
  case path(path: String)
  case socket(_ path: String)
}
