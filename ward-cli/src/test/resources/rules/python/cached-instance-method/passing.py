import functools
@functools.lru_cache
def m(x):
    return x


class K:
    @cache.memoize(timeout=60)
    def m(self):
        return 1
