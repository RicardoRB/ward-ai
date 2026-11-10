import functools


class A:
    @functools.lru_cache(maxsize=128)
    def m(self, x):
        return x
