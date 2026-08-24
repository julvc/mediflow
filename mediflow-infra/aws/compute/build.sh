#!/usr/bin/env bash
# Arma lambda/ antes de tflocal apply: copia procesador/ desde medi-python
# (sin modificarlo — es el mismo codigo agnostico de nube) e instala sus
# dependencias como wheels manylinux (Lambda corre Linux; --platform baja el
# wheel prebuilto correcto sin compilar nada, incluso corriendo esto en
# Windows). No versionar lambda/procesador ni las libs instaladas — quedan
# en .gitignore, se regeneran corriendo este script.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf lambda/procesador
cp -r ../../../medi-python/procesador lambda/procesador
find lambda/procesador -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true

python -m pip install \
  --platform manylinux2014_x86_64 \
  --python-version 3.12 \
  --implementation cp \
  --abi cp312 \
  --only-binary=:all: \
  --target lambda \
  -r lambda/requirements.txt

rm -rf lambda/*.dist-info lambda/bin
find lambda -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true

echo "lambda/ listo ($(du -sh lambda | cut -f1))"
