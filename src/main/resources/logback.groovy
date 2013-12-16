appender('console', ConsoleAppender) {
    encoder(PatternLayoutEncoder) { pattern = "%level: %msg%n" }
}

root debug, ['console']

