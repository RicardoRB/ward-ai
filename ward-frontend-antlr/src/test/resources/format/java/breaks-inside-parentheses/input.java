class Messages {
    void log(String agent, Request request) {
        LOGGER.log(Level.INFO, () ->
            "event=start agent="
            + agent
            + " guard="
            + request.guard());
        if (request.remove()
            && request.agent().equals(agent)) {
            remove(agent);
        }
        send(request.items().stream()
            .map(Item::name)
            .toList());
    }
}
