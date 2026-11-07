def handle(command: str) -> str:
    match command.split():
        case [action, *rest]:
            return action
        case _:
            return "unknown"
