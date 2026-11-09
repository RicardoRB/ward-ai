class Config:
    def __init__(self):
        self.host = "localhost"
        self.port: int = 8080

    def address(self):
        return f"{self.host}:{self.port}"

    def describe(self):
        return str(self.port) + self.host
