class Config:
    def __init__(self):
        self.host = "localhost"
        self.port: int = 8080
        self.debug = False

    def address(self):
        return f"{self.host}:{self.port}"

    def describe(self):
        return str(getattr(self, "debug")) + self.host
