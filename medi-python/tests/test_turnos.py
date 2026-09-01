from datetime import datetime, timedelta, timezone


def _fecha_futura() -> str:
    return (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()


def test_crear_turno_pendiente(client, headers_auth, paciente_y_profesional):
    paciente_id, profesional_id = paciente_y_profesional

    respuesta = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": _fecha_futura(), "motivo": "Control"},
        headers=headers_auth,
    )

    assert respuesta.status_code == 201
    assert respuesta.json()["estado"] == "PENDIENTE"


def test_crear_turno_con_profesional_ya_ocupado_devuelve_409(client, headers_auth, paciente_y_profesional):
    paciente_id, profesional_id = paciente_y_profesional
    fecha = _fecha_futura()
    client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": fecha},
        headers=headers_auth,
    )

    respuesta = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": fecha},
        headers=headers_auth,
    )

    assert respuesta.status_code == 409


def test_crear_turno_en_el_pasado_devuelve_422(client, headers_auth, paciente_y_profesional):
    paciente_id, profesional_id = paciente_y_profesional
    fecha_pasada = (datetime.now(timezone.utc) - timedelta(hours=1)).isoformat()

    respuesta = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": fecha_pasada},
        headers=headers_auth,
    )

    assert respuesta.status_code == 422


def test_cambiar_estado_transicion_valida(client, headers_auth, paciente_y_profesional):
    paciente_id, profesional_id = paciente_y_profesional
    turno_id = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": _fecha_futura()},
        headers=headers_auth,
    ).json()["id"]

    respuesta = client.patch(f"/turnos/{turno_id}/estado", json={"estado": "CONFIRMADO"}, headers=headers_auth)

    assert respuesta.status_code == 200
    assert respuesta.json()["estado"] == "CONFIRMADO"


def test_cambiar_estado_transicion_invalida_devuelve_409(client, headers_auth, paciente_y_profesional):
    paciente_id, profesional_id = paciente_y_profesional
    turno_id = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": _fecha_futura()},
        headers=headers_auth,
    ).json()["id"]

    respuesta = client.patch(f"/turnos/{turno_id}/estado", json={"estado": "COMPLETADO"}, headers=headers_auth)

    assert respuesta.status_code == 409


def test_listar_por_paciente_filtra_correctamente(client, headers_auth, paciente_y_profesional):
    paciente_id, profesional_id = paciente_y_profesional
    client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": _fecha_futura()},
        headers=headers_auth,
    )

    respuesta = client.get(f"/turnos?paciente_id={paciente_id}", headers=headers_auth)

    assert respuesta.status_code == 200
    assert all(t["paciente_id"] == paciente_id for t in respuesta.json())
