package example

context(logger: Logger)
fun log(message: String) {
    logger.info(message)
}
