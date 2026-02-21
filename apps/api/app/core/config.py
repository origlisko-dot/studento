from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Viral Proxy Lab API"
    api_prefix: str = "/api"
    database_url: str = "postgresql+psycopg://viralproxy:viralproxy@localhost:5432/viralproxylab"
    redis_url: str = "redis://localhost:6379/0"
    upload_dir: str = str(Path(__file__).resolve().parents[4] / "storage" / "uploads")
    max_upload_size_mb: int = 200

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", case_sensitive=False)


@lru_cache
def get_settings() -> Settings:
    return Settings()
