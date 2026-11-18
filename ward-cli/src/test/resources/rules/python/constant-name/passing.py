from fastapi import FastAPI

MAX_SIZE = 10
app = FastAPI()
urlpatterns = [app]
store = {}
greeting = f"hi {MAX_SIZE}"
api = Blueprint("api", __name__, url_prefix="/api/v1")
