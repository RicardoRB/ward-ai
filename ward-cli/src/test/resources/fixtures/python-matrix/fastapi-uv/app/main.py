"""Fixture code for the Python matrix."""

from fastapi import FastAPI

from app.routers.orders import router

app = FastAPI()
app.include_router(router)
