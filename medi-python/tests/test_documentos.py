def _datos_documento(turno_id):
    return {"turno_id": turno_id, "nombre_archivo": "examen.pdf", "url_storage": "https://example.com/examen.pdf", "tipo_documento": "Examen de laboratorio"}


def test_crear_documento_pendiente(client, headers_auth, turno_id):
    respuesta = client.post("/documentos", json=_datos_documento(turno_id), headers=headers_auth)

    assert respuesta.status_code == 201
    assert respuesta.json()["estado"] == "PENDIENTE"


def test_crear_documento_con_turno_inexistente_devuelve_404(client, headers_auth):
    respuesta = client.post("/documentos", json=_datos_documento(999999), headers=headers_auth)

    assert respuesta.status_code == 404


def test_listar_por_turno(client, headers_auth, turno_id):
    client.post("/documentos", json=_datos_documento(turno_id), headers=headers_auth)

    respuesta = client.get(f"/documentos?turno_id={turno_id}", headers=headers_auth)

    assert respuesta.status_code == 200
    assert len(respuesta.json()) >= 1


def test_cambiar_estado_a_procesado(client, headers_auth, turno_id):
    documento_id = client.post("/documentos", json=_datos_documento(turno_id), headers=headers_auth).json()["id"]

    respuesta = client.patch(f"/documentos/{documento_id}/estado", json={"estado": "PROCESADO"}, headers=headers_auth)

    assert respuesta.status_code == 200
    assert respuesta.json()["estado"] == "PROCESADO"


def test_cambiar_estado_terminal_devuelve_409(client, headers_auth, turno_id):
    documento_id = client.post("/documentos", json=_datos_documento(turno_id), headers=headers_auth).json()["id"]
    client.patch(f"/documentos/{documento_id}/estado", json={"estado": "PROCESADO"}, headers=headers_auth)

    respuesta = client.patch(f"/documentos/{documento_id}/estado", json={"estado": "ERROR"}, headers=headers_auth)

    assert respuesta.status_code == 409


def test_eliminar_documento(client, headers_auth, turno_id):
    documento_id = client.post("/documentos", json=_datos_documento(turno_id), headers=headers_auth).json()["id"]

    respuesta_delete = client.delete(f"/documentos/{documento_id}", headers=headers_auth)
    assert respuesta_delete.status_code == 204

    respuesta_get = client.get(f"/documentos/{documento_id}", headers=headers_auth)
    assert respuesta_get.status_code == 404
