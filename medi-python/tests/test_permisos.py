from datetime import datetime, timedelta, timezone


def test_paciente_no_puede_crear_paciente(client, paciente_autenticado):
    _, headers = paciente_autenticado

    respuesta = client.post(
        "/pacientes",
        json={"rut": "50505050-5", "nombres": "X", "apellidos": "Y", "fecha_nacimiento": "1990-01-01", "email": "x@test.cl"},
        headers=headers,
    )

    assert respuesta.status_code == 403


def test_paciente_no_puede_crear_profesional(client, paciente_autenticado):
    _, headers = paciente_autenticado

    respuesta = client.post(
        "/profesionales",
        json={"rut": "60606060-6", "nombres": "X", "apellidos": "Y", "especialidad": "Z", "email": "x@test.cl"},
        headers=headers,
    )

    assert respuesta.status_code == 403


def test_paciente_no_puede_listar_todos_los_pacientes(client, paciente_autenticado):
    _, headers = paciente_autenticado

    respuesta = client.get("/pacientes", headers=headers)

    assert respuesta.status_code == 403


def test_paciente_puede_ver_su_propia_ficha(client, paciente_autenticado):
    paciente_id, headers = paciente_autenticado

    respuesta = client.get(f"/pacientes/{paciente_id}", headers=headers)

    assert respuesta.status_code == 200


def test_paciente_no_puede_ver_ficha_ajena(client, headers_auth, paciente_autenticado):
    _, headers_paciente = paciente_autenticado
    otro = client.post(
        "/pacientes",
        json={"rut": "70707070-7", "nombres": "Otro", "apellidos": "Paciente", "fecha_nacimiento": "1990-01-01", "email": "otro@test.cl"},
        headers=headers_auth,
    ).json()

    respuesta = client.get(f"/pacientes/{otro['id']}", headers=headers_paciente)

    assert respuesta.status_code == 403


def test_paciente_agenda_su_propio_turno_y_lo_ve(client, headers_auth, paciente_autenticado):
    paciente_id, headers_paciente = paciente_autenticado
    profesional = client.post(
        "/profesionales",
        json={"rut": "80808080-8", "nombres": "Prof", "apellidos": "Test", "especialidad": "Kinesiología", "email": "prof@test.cl"},
        headers=headers_auth,
    ).json()
    fecha = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()

    turno = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional["id"], "fecha_hora": fecha},
        headers=headers_paciente,
    )
    assert turno.status_code == 201
    turno_id = turno.json()["id"]

    respuesta_get = client.get(f"/turnos/{turno_id}", headers=headers_paciente)
    assert respuesta_get.status_code == 200


def test_paciente_no_puede_ver_turno_ajeno(client, headers_auth, paciente_autenticado, turno_id):
    _, headers_paciente = paciente_autenticado

    respuesta = client.get(f"/turnos/{turno_id}", headers=headers_paciente)

    assert respuesta.status_code == 403


def test_paciente_no_puede_confirmar_su_propio_turno_pero_si_cancelarlo(client, headers_auth, paciente_autenticado):
    paciente_id, headers_paciente = paciente_autenticado
    profesional = client.post(
        "/profesionales",
        json={"rut": "81818181-8", "nombres": "Prof", "apellidos": "Dos", "especialidad": "Kinesiología", "email": "prof2@test.cl"},
        headers=headers_auth,
    ).json()
    fecha = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    turno_id = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional["id"], "fecha_hora": fecha},
        headers=headers_paciente,
    ).json()["id"]

    respuesta_confirmar = client.patch(f"/turnos/{turno_id}/estado", json={"estado": "CONFIRMADO"}, headers=headers_paciente)
    assert respuesta_confirmar.status_code == 403

    respuesta_cancelar = client.patch(f"/turnos/{turno_id}/estado", json={"estado": "CANCELADO"}, headers=headers_paciente)
    assert respuesta_cancelar.status_code == 200


def test_paciente_no_puede_listar_documentos_de_su_turno(client, headers_auth, paciente_autenticado):
    paciente_id, headers_paciente = paciente_autenticado
    profesional = client.post(
        "/profesionales",
        json={"rut": "82828282-8", "nombres": "Prof", "apellidos": "Tres", "especialidad": "Kinesiología", "email": "prof3@test.cl"},
        headers=headers_auth,
    ).json()
    fecha = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    turno_id = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional["id"], "fecha_hora": fecha},
        headers=headers_paciente,
    ).json()["id"]

    # POST /documentos si esta abierto a cualquier rol (mismo diseño que Java).
    creado = client.post(
        "/documentos",
        json={"turno_id": turno_id, "nombre_archivo": "examen.pdf", "url_storage": "https://x.test/e.pdf", "tipo_documento": "Examen"},
        headers=headers_paciente,
    )
    assert creado.status_code == 201

    respuesta_listar = client.get(f"/documentos?turno_id={turno_id}", headers=headers_paciente)
    assert respuesta_listar.status_code == 403
