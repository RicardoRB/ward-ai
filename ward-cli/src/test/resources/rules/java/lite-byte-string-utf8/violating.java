final class Violating {
  String run(com.google.protobuf.MessageLite message) {
    return message.toByteString().toStringUtf8();
  }
}
