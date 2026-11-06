import os
from json import loads

LIMIT = 3


class Box:
    size = LIMIT

    def grow(self, step):
        global LIMIT
        LIMIT = step
        return [n + step for n in range(self.size)]


def run(text):
    data = loads(text)
    return os.path.join(str(data), f"{LIMIT}")
