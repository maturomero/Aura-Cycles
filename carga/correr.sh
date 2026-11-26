#!/usr/bin/env bash
# Corre la prueba de carga con k6 en Docker contra la app levantada (docker compose up). Deja el resumen en carga/resultados
# Uso: carga/correr.sh            -> pantallas (catalogo, detalle, mis compras)
#      carga/correr.sh carga-api.js -> API REST (devoluciones, detalle de devolucion, resenas)
set -euo pipefail
cd "$(dirname "$0")"
SCRIPT=${1:-carga.js}
NOMBRE=${SCRIPT%.js}
mkdir -p resultados && chmod 777 resultados
docker run --rm --network host -e APP_URL="${APP_URL:-https://localhost:8443/repuestos}" \
  -v "$PWD:/carga" grafana/k6:0.55.0 run --summary-export="/carga/resultados/$NOMBRE.json" "/carga/$SCRIPT" \
  | tee "resultados/$NOMBRE.txt"
