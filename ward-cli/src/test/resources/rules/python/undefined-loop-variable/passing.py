def f():
    for item in range(3):
        pass
    return item


def names(items, rows):
    for obj in items:
        print(obj)
    return [obj.name for obj in rows]


def alive(threads):
    for thread in threads:
        print(thread)
    return not any(thread.done for thread in threads)
