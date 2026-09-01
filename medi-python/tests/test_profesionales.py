def _datos_profesional(rut="66666666-6"):
    return {
        "rut": rut,
        "nombres": "Jorge",
        "apellidos": "Perez",
        "especialidad": "Medicina general",
        "email": "jorge@test.cl",
    }


def test_crear_y_obtener_profesional(client, headers_auth):
    respuesta_crear = client.post("/profesionales", json=_datos_profesional("77777777-7"), headers=headers_auth)
    assert respuesta_crear.status_code == 201
    id_creado = respuesta_crear.json()["id"]

    respuesta_get = client.get(f"/profesionales/{id_creado}", headers=headers_auth)
    assert respuesta_get.status_code == 200
    assert respuesta_get.json()["especialidad"] == "Medicina general"


def test_crear_profesional_con_rut_duplicado_devuelve_409(client, headers_auth):
    client.post("/profesionales", json=_datos_profesional("88888888-8"), headers=headers_auth)

    respuesta = client.post("/profesionales", json=_datos_profesional("88888888-8"), headers=headers_auth)

    assert respuesta.status_code == 409


def test_eliminar_profesional(client, headers_auth):
    id_creado = client.post(
        "/profesionales", json=_datos_profesional("99999999-9"), headers=headers_auth
    ).json()["id"]

    respuesta_delete = client.delete(f"/profesionales/{id_creado}", headers=headers_auth)
    assert respuesta_delete.status_code == 204

    respuesta_get = client.get(f"/profesionales/{id_creado}", headers=headers_auth)
    assert respuesta_get.status_code == 404
