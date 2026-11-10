import functools
class K:
    @functools.lru_cache
    def m(self):
        return 1
