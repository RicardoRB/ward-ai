from fastapi import FastAPI

MAX_SIZE = 10
app = FastAPI()
urlpatterns = [app]
store = {}
greeting = f"hi {MAX_SIZE}"
