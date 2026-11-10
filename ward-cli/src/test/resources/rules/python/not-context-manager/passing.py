class A:
    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass


with A():
    pass
