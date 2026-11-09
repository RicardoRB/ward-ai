try:
    raise ExceptionGroup("failed", [ValueError(1), TypeError(2)])
except* ValueError as group:
    print(group)
except* (TypeError, KeyError):
    pass
