from pydantic import BaseModel


class Bad(BaseModel):
    count: int = "three"
