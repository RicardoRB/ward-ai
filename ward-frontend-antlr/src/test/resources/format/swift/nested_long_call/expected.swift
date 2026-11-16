func build(queue: Queue, stream: Stream) {
    components.append((
        escape(key),
        escape(encoder.encode(value: number.boolValue, using: configuration))
    ))
    respond(using: DecodableSerializer<Item>(
        decoder: decoder,
        preprocessor: preprocessor,
        emptyCodes: codes
    ),
    on: queue,
    stream: stream)
}
