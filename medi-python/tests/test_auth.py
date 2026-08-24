import itertools

_contador = itertools.count(90000000)


def _crear_paciente_y_registrar(client, headers_auth, email):
    paciente = client.post(
        "/pacientes",
        json={
            "rut": f"{next(_contador)}-5",
            "nombres": "Ana",
            "apellidos": "Soto",
            "fecha_nacimiento": "1990-01-01",
            "email": email,
        },
        headers=headers_auth,
    ).json()
    return paciente["id"]


def test_registro_y_login_devuelve_tokens(client, headers_auth):
    paciente_id = _crear_paciente_y_registrar(client, headers_auth, "ana@mediflow.cl")

    respuesta_registro = client.post(
        "/auth/registro",
        json={"email": "ana@mediflow.cl", "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente_id},
    )
    assert respuesta_registro.status_code == 201
    assert respuesta_registro.json()["rol"] == "PACIENTE"

    respuesta_login = client.post("/auth/login", json={"email": "ana@mediflow.cl", "password": "clave1234"})
    assert respuesta_login.status_code == 200
    cuerpo = respuesta_login.json()
    assert cuerpo["access_token"]
    assert cuerpo["refresh_token"]


def test_registro_paciente_con_email_que_no_coincide_devuelve_404(client, headers_auth):
    paciente_id = _crear_paciente_y_registrar(client, headers_auth, "dueno-real@mediflow.cl")

    respuesta = client.post(
        "/auth/registro",
        json={"email": "atacante@mediflow.cl", "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente_id},
    )

    assert respuesta.status_code == 404


def test_registro_admin_sin_estar_autenticado_como_admin_devuelve_403(client):
    respuesta = client.post(
        "/auth/registro", json={"email": "nuevo-admin@mediflow.cl", "password": "clave1234", "rol": "ADMIN"}
    )

    assert respuesta.status_code == 403


def test_registro_profesional_como_admin_funciona(client, headers_auth):
    profesional = client.post(
        "/profesionales",
        json={"rut": f"{next(_contador)}-5", "nombres": "Luis", "apellidos": "Diaz", "especialidad": "Cardiología", "email": "luis@mediflow.cl"},
        headers=headers_auth,
    ).json()

    respuesta = client.post(
        "/auth/registro",
        json={"email": "luis@mediflow.cl", "password": "clave1234", "rol": "PROFESIONAL", "profesional_id": profesional["id"]},
        headers=headers_auth,
    )

    assert respuesta.status_code == 201


def test_registro_con_email_duplicado_devuelve_409(client, headers_auth):
    paciente_id = _crear_paciente_y_registrar(client, headers_auth, "duplicado@mediflow.cl")
    client.post(
        "/auth/registro",
        json={"email": "duplicado@mediflow.cl", "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente_id},
    )

    respuesta = client.post(
        "/auth/registro",
        json={"email": "duplicado@mediflow.cl", "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente_id},
    )

    assert respuesta.status_code == 409


def test_login_con_password_incorrecto_devuelve_401(client, headers_auth):
    paciente_id = _crear_paciente_y_registrar(client, headers_auth, "carlos@mediflow.cl")
    client.post(
        "/auth/registro",
        json={"email": "carlos@mediflow.cl", "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente_id},
    )

    respuesta = client.post("/auth/login", json={"email": "carlos@mediflow.cl", "password": "otra-clave"})

    assert respuesta.status_code == 401


def test_pacientes_sin_token_devuelve_401(client):
    respuesta = client.get("/pacientes")

    assert respuesta.status_code == 401


def test_refresh_rota_el_token_y_el_original_queda_invalido(client, headers_auth):
    paciente_id = _crear_paciente_y_registrar(client, headers_auth, "refresh@mediflow.cl")
    client.post(
        "/auth/registro",
        json={"email": "refresh@mediflow.cl", "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente_id},
    )
    refresh_original = client.post("/auth/login", json={"email": "refresh@mediflow.cl", "password": "clave1234"}).json()["refresh_token"]

    primer_refresh = client.post("/auth/refresh", json={"refresh_token": refresh_original})
    assert primer_refresh.status_code == 200
    assert primer_refresh.json()["refresh_token"] != refresh_original

    segundo_intento = client.post("/auth/refresh", json={"refresh_token": refresh_original})
    assert segundo_intento.status_code == 401


def test_logout_revoca_el_refresh_token(client, headers_auth):
    paciente_id = _crear_paciente_y_registrar(client, headers_auth, "logout@mediflow.cl")
    client.post(
        "/auth/registro",
        json={"email": "logout@mediflow.cl", "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente_id},
    )
    refresh_token = client.post("/auth/login", json={"email": "logout@mediflow.cl", "password": "clave1234"}).json()["refresh_token"]

    respuesta_logout = client.post("/auth/logout", json={"refresh_token": refresh_token})
    assert respuesta_logout.status_code == 204

    respuesta_refresh = client.post("/auth/refresh", json={"refresh_token": refresh_token})
    assert respuesta_refresh.status_code == 401
