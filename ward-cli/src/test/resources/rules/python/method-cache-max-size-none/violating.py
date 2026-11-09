import functools


class A:
    @functools.lru_cache(maxsize=None)
    def m(self, x):
        return x
