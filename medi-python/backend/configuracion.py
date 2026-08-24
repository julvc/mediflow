from pydantic_settings import BaseSettings, SettingsConfigDict


class Configuracion(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MEDIFLOW_PY_")

    database_url: str = "postgresql://postgres:local@localhost:5433/mediflow_python"
    jwt_secret: str = "mediflow-python-dev-secret-cambiar-en-produccion"
    jwt_expira_minutos: int = 15
    refresh_token_dias: int = 7


configuracion = Configuracion()
