class A:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass


async def f():
    async with A():
        pass
