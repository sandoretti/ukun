import { BASE_URL } from '../playwright.config';

/** Lo que se tarda en descartar que el stack esté simplemente arrancando. */
const PROBE_TIMEOUT_MS = 5_000;

/**
 * Comprueba que hay un stack al otro lado antes de abrir el navegador.
 *
 * Sin esto, el modo de fallo de «se me olvidó `tilt up`» es un `net::ERR_CONNECTION_REFUSED` dentro
 * del primer `page.goto`, con una traza y un vídeo de una pestaña en blanco. El coste de esta
 * comprobación es una petición; lo que ahorra es leer un informe para descubrir que no había nada
 * que probar.
 *
 * Se pregunta por la salud del gateway y no por la portada: `/actuator/health` responde sin depender
 * de que el bundle esté construido, que es un fallo distinto y con otro remedio.
 */
export default async function globalSetup(): Promise<void> {
  const url = `${BASE_URL}/actuator/health`;

  const response = await fetch(url, { signal: AbortSignal.timeout(PROBE_TIMEOUT_MS) }).catch(
    (cause: unknown) => {
      throw new Error(
        `No hay stack en ${BASE_URL}. Arranca \`tilt up\` y espera a que los recursos estén verdes` +
          ` (docs/06-entorno-local-tilt.md). Si tu stack va en otros puertos —un worktree—, exporta` +
          ` E2E_BASE_URL.`,
        { cause },
      );
    },
  );

  if (!response.ok) {
    throw new Error(`El gateway de ${BASE_URL} responde ${response.status} a /actuator/health.`);
  }
}
