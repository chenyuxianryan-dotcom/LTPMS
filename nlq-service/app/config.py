from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    deepseek_api_key: str
    deepseek_api_url: str = "https://api.deepseek.com/chat/completions"
    deepseek_model: str = "deepseek-flash"
    deepseek_timeout: float = 15.0

    # 视觉识别（图片理解）：默认与 NL2SQL/Agent 共用 DeepSeek 账号与端点
    # （deepseek_model 支持图像理解）。
    # vision_provider=zhipu 是回退开关：统一通道异常时可临时切回智谱 GLM-4V，
    # 无需改代码（切换后调用量应归 0，只在一个发布周期内保留）。
    vision_provider: str = "deepseek"
    zhipu_api_key: str = ""
    zhipu_api_url: str = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    zhipu_vision_model: str = "glm-4v-flash"

    # DashScope 文本向量化（知识库检索）；key 为空时知识库检索优雅降级
    dashscope_api_key: str = ""
    embedding_api_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"
    embedding_model: str = "text-embedding-v4"
    embedding_dim: int = 1024
    embedding_timeout: float = 30.0

    db_host: str = "127.0.0.1"
    db_port: int = 3306
    db_name: str = "ltpms_db"
    db_user: str = "ltpms_readonly"
    db_password: str = ""

    nlq_internal_token: str = ""

    skills_dir: str = "skills"
    memory_db_path: str = "data/agent_memory.db"

    # 实验材料落盘目录（数据集文件画像只读访问）；dev 指向后端 backend/uploads/lab，
    # 相对路径以 nlq-service 工作目录为基准，生产用 env 覆盖为绝对路径。
    lab_upload_dir: str = "../backend/uploads/lab"
    # 单文件画像采样上限：超过则降级为仅元数据（不解析内容，不报错）
    profile_max_file_mb: int = 20
    profile_sample_rows: int = 2000
    # 报告生成的独立超时（秒）：比全局 15s 宽，六节长文一次成文
    report_timeout: float = 45.0

    # MCP Server（V3）：只读数据工具对外暴露；mcp_enabled=False 时拒绝启动（生产默认关）
    mcp_enabled: bool = True
    mcp_host: str = "127.0.0.1"
    mcp_port: int = 8800
    mcp_token: str = ""  # SSE 模式访问令牌；为空则不校验（仅限本机演示）
    mcp_max_rows: int = 100

    host: str = "0.0.0.0"
    port: int = 8001

    class Config:
        env_file = ".env"


settings = Settings()
