import json


@decorator(json)
def work(path):
    with open(path) as handle:
        data = handle.read()
    try:
        pass
    except OSError as error:
        pass
    for index, value in enumerate(data):
        value += 1
    first, *rest = data
    del data
    label = f"{path!r:>{width}} {rest[0]} {first.attr}"
    count: int = 0
    note: str
    return label, count


work = work
__all__ = ["work", "json"]
