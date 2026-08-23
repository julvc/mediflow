def test_registro_y_login_devuelve_token(client):
    respuesta_registro = client.post(
        "/auth/registro", json={"email": "ana@mediflow.cl", "password": "clave1234"}
    )
    assert respuesta_registro.status_code == 201

    respuesta_login = client.post("/auth/login", json={"email": "ana@mediflow.cl", "password": "clave1234"})
    assert respuesta_login.status_code == 200
    assert respuesta_login.json()["access_token"]


def test_registro_con_email_duplicado_devuelve_409(client):
    client.post("/auth/registro", json={"email": "duplicado@mediflow.cl", "password": "clave1234"})

    respuesta = client.post("/auth/registro", json={"email": "duplicado@mediflow.cl", "password": "clave1234"})

    assert respuesta.status_code == 409


def test_login_con_password_incorrecto_devuelve_401(client):
    client.post("/auth/registro", json={"email": "carlos@mediflow.cl", "password": "clave1234"})

    respuesta = client.post("/auth/login", json={"email": "carlos@mediflow.cl", "password": "otra-clave"})

    assert respuesta.status_code == 401


def test_pacientes_sin_token_devuelve_401(client):
    respuesta = client.get("/pacientes")

    assert respuesta.status_code == 401
