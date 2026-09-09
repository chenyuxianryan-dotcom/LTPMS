from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    deepseek_api_key: str
    deepseek_api_url: str = "https://api.deepseek.com/chat/completions"
    deepseek_model: str = "deepseek-chat"
    deepseek_timeout: float = 15.0

    # 智谱 GLM-4V 视觉（课表照片识别）；key 为空时视觉能力优雅降级，不影响查询类能力
    zhipu_api_key: str = ""
    zhipu_api_url: str = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    zhipu_vision_model: str = "glm-4v-flash"

    db_host: str = "127.0.0.1"
    db_port: int = 3306
    db_name: str = "ltpms_db"
    db_user: str = "ltpms_readonly"
    db_password: str = ""

    nlq_internal_token: str = ""

    skills_dir: str = "skills"
    memory_db_path: str = "data/agent_memory.db"

    host: str = "0.0.0.0"
    port: int = 8001

    class Config:
        env_file = ".env"


settings = Settings()
