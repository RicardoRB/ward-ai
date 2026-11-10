def first(*items: *tuple[int, ...]):
    return items[*range(2)]
