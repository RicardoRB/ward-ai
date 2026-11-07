try:
    raise ExceptionGroup("group", [ValueError("bad")])
except* ValueError as group:
    print(group)
