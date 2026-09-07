from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    deepseek_api_key: str
    deepseek_api_url: str = "https://api.deepseek.com/chat/completions"
    deepseek_model: str = "deepseek-chat"
    deepseek_timeout: float = 15.0

    db_host: str = "127.0.0.1"
    db_port: int = 3306
    db_name: str = "ltpms_db"
    db_user: str = "ltpms_readonly"
    db_password: str = ""

    nlq_internal_token: str = ""

    host: str = "0.0.0.0"
    port: int = 8001

    class Config:
        env_file = ".env"


settings = Settings()
