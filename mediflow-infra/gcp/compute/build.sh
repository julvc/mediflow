#!/usr/bin/env bash
# Arma function/ antes de terraform apply: copia procesador/ desde
# medi-python (sin modificarlo). A diferencia de build.sh en aws/, aca NO se
# instalan wheels a mano — Cloud Functions gen2 corre pip install -r
# requirements.txt del lado del servidor (buildpacks de Cloud Build), asi
# que basta con que el zip traiga el codigo fuente. Diferencia real entre
# plataformas, no un atajo nuestro.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf function/procesador
cp -r ../../../medi-python/procesador function/procesador
find function/procesador -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true

echo "function/ listo ($(du -sh function | cut -f1))"
