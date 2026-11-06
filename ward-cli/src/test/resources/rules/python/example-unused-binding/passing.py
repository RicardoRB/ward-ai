def work(path):
    total = 0
    _ignored = 1
    first, second = path
    for index in range(3):
        total += index
    with open(path) as handle:
        total += len(handle.read())
    return total, first
