// Prueba de carga: 50 usuarios logueados durante 5 minutos sobre los 3 endpoints mas usados.
// Criterio: p95 < 500 ms y menos de 1 % de errores en cada uno. Correr con: carga/correr.sh
import http from 'k6/http';
import { check, sleep } from 'k6';

const APP = __ENV.APP_URL || 'https://localhost:8443/repuestos';
const USUARIOS = ['ana@mail.com', 'carlos@mail.com', 'lucia@mail.com', 'javier@mail.com'];

const umbrales = {};
for (const e of ['catalogo', 'detalle', 'compras']) {
  umbrales[`http_req_duration{endpoint:${e}}`] = ['p(95)<500'];
  umbrales[`http_req_failed{endpoint:${e}}`] = ['rate<0.01'];
}

export const options = {
  insecureSkipTLSVerify: true,
  noCookiesReset: true, // la sesion dura todas las iteraciones del usuario virtual
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

// Cada usuario virtual se loguea una sola vez (bcrypt es lento a proposito; no es lo que se mide aca)
function loguear() {
  const email = USUARIOS[(__VU - 1) % USUARIOS.length];
  const pagina = http.get(`${APP}/login.xhtml`, { tags: { endpoint: 'login' } });
  const doc = pagina.html();
  const res = pagina.submitForm({
    formSelector: 'form:has(input[id$="email"])',
    fields: {
      [doc.find('input[id$="email"]').attr('name')]: email,
      [doc.find('input[id$="pass"]').attr('name')]: 'Clave1234',
    },
    submitSelector: 'input[value="Ingresar"]',
    params: { tags: { endpoint: 'login' } },
  });
  check(res, { 'login ok': (r) => r.status === 200 && !r.url.includes('login.xhtml') });
}

let logueado = false;

export default function () {
  if (!logueado) {
    loguear();
    logueado = true;
  }
  let r = http.get(`${APP}/productos.xhtml`, { tags: { endpoint: 'catalogo' } });
  check(r, { 'catalogo 200': (x) => x.status === 200 && x.body.includes('Productos') });
  sleep(1);

  r = http.get(`${APP}/producto.xhtml?productoId=${1 + Math.floor(Math.random() * 7)}`, { tags: { endpoint: 'detalle' } });
  check(r, { 'detalle 200': (x) => x.status === 200 && x.body.includes('Comprar') });
  sleep(1);

  r = http.get(`${APP}/compras.xhtml`, { tags: { endpoint: 'compras' } });
  check(r, { 'compras 200': (x) => x.status === 200 && x.body.includes('Mis compras') });
  sleep(1);
}
