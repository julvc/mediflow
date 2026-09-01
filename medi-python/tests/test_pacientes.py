def _datos_paciente(rut="11111111-1"):
    return {
        "rut": rut,
        "nombres": "Ana",
        "apellidos": "Soto",
        "fecha_nacimiento": "1990-01-01",
        "email": "ana.soto@test.cl",
        "telefono": "+56911112222",
    }


def test_crear_y_obtener_paciente(client, headers_auth):
    respuesta_crear = client.post("/pacientes", json=_datos_paciente("22222222-2"), headers=headers_auth)
    assert respuesta_crear.status_code == 201
    id_creado = respuesta_crear.json()["id"]

    respuesta_get = client.get(f"/pacientes/{id_creado}", headers=headers_auth)
    assert respuesta_get.status_code == 200
    assert respuesta_get.json()["rut"] == "22222222-2"


def test_crear_paciente_con_rut_duplicado_devuelve_409(client, headers_auth):
    client.post("/pacientes", json=_datos_paciente("33333333-3"), headers=headers_auth)

    respuesta = client.post("/pacientes", json=_datos_paciente("33333333-3"), headers=headers_auth)

    assert respuesta.status_code == 409


def test_crear_paciente_con_rut_invalido_devuelve_422(client, headers_auth):
    datos = _datos_paciente()
    datos["rut"] = "no-es-un-rut"

    respuesta = client.post("/pacientes", json=datos, headers=headers_auth)

    assert respuesta.status_code == 422
    assert "rut" in respuesta.json()["detalles"][0]


def test_obtener_paciente_inexistente_devuelve_404(client, headers_auth):
    respuesta = client.get("/pacientes/999999", headers=headers_auth)

    assert respuesta.status_code == 404


def test_actualizar_paciente(client, headers_auth):
    id_creado = client.post("/pacientes", json=_datos_paciente("44444444-4"), headers=headers_auth).json()["id"]
    datos_actualizados = _datos_paciente("44444444-4")
    datos_actualizados["telefono"] = "+56999998888"

    respuesta = client.put(f"/pacientes/{id_creado}", json=datos_actualizados, headers=headers_auth)

    assert respuesta.status_code == 200
    assert respuesta.json()["telefono"] == "+56999998888"


def test_eliminar_paciente(client, headers_auth):
    id_creado = client.post("/pacientes", json=_datos_paciente("55555555-5"), headers=headers_auth).json()["id"]

    respuesta_delete = client.delete(f"/pacientes/{id_creado}", headers=headers_auth)
    assert respuesta_delete.status_code == 204

    respuesta_get = client.get(f"/pacientes/{id_creado}", headers=headers_auth)
    assert respuesta_get.status_code == 404
