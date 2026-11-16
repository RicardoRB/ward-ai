def run(item):
    return item


def find(items, wanted):
    for item in items:
        if item == wanted:
            return item
    return None
