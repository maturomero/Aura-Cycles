// Prueba de carga de la API REST: 50 usuarios durante 5 minutos sobre los 3 endpoints criticos de la API.
// Criterio: p95 < 500 ms y menos de 1 % de errores en cada uno. Correr con: carga/correr.sh carga-api.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const API = (__ENV.APP_URL || 'https://localhost:8443/repuestos') + '/api/v1';
const CLIENTES = ['ana@mail.com', 'carlos@mail.com', 'lucia@mail.com', 'javier@mail.com'];

const umbrales = {};
for (const e of ['devoluciones', 'devolucion', 'resenas']) {
  umbrales[`http_req_duration{endpoint:${e}}`] = ['p(95)<500'];
  umbrales[`http_req_failed{endpoint:${e}}`] = ['rate<0.01'];
}

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    carga: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 50 },
        { duration: '4m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: { ...umbrales, checks: ['rate>0.99'] },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max'],
};

function login(email) {
  const r = http.post(`${API}/auth/login`, JSON.stringify({ email, password: 'Clave1234' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } });
  return r.json('token');
}

// Un login por usuario al principio: el JWT dura 15 minutos y la prueba 5
export function setup() {
  const moderador = login('mod@mail.com');
  const lista = http.get(`${API}/devoluciones`, { headers: { Authorization: `Bearer ${moderador}` } });
  return { moderador, clientes: CLIENTES.map(login), ids: lista.json('items').map((d) => d.id) };
}

export default function (datos) {
  const cliente = { headers: { Authorization: `Bearer ${datos.clientes[(__VU - 1) % datos.clientes.length]}` } };
  const moderador = { headers: { Authorization: `Bearer ${datos.moderador}` } };

  let r = http.get(`${API}/devoluciones`, { ...cliente, tags: { endpoint: 'devoluciones' } });
  check(r, { 'devoluciones 200': (x) => x.status === 200 });
  sleep(1);

  const id = datos.ids[Math.floor(Math.random() * datos.ids.length)];
  r = http.get(`${API}/devoluciones/${id}`, { ...moderador, tags: { endpoint: 'devolucion' } });
  check(r, { 'devolucion 200': (x) => x.status === 200 });
  sleep(1);

  r = http.get(`${API}/productos/${1 + Math.floor(Math.random() * 7)}/resenas`, { ...cliente, tags: { endpoint: 'resenas' } });
  check(r, { 'resenas 200': (x) => x.status === 200 });
  sleep(1);
}
