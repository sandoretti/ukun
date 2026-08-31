import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

/** El contenido de la ruta, en los tres cascarones. Es también el destino del salto al contenido. */
const MAIN_CONTENT_ID = 'main-content';

/**
 * Mueve el foco al contenido cada vez que cambia la ruta.
 *
 * En una página servida por el servidor, navegar es cargar un documento: el foco vuelve al principio
 * y el lector de pantalla empieza a leer. En un SPA no pasa **nada** — se sustituye un trozo del DOM
 * y el foco se queda donde estaba, o en `<body>` si el elemento que lo tenía ha dejado de existir. El
 * resultado es que un cambio de pantalla es completamente mudo, y este repo tiene cuatro sitios donde
 * la confirmación de que algo ha salido bien *es* la pantalla siguiente: dar de alta una línea de
 * stock, un empleado o un producto, y confirmar un pedido. Quien no ve la pantalla no se entera de
 * que ha funcionado. El quinto caso es borrar un producto desde su diálogo: `restoreFocus` de
 * react-bootstrap devuelve el foco a un botón que ya no existe, y cae en `<body>`.
 *
 * Enfocar el contenedor es lo que un lector de pantalla necesita para volver a hablar: anuncia la
 * región y sigue por lo que hay dentro. No se anuncia además un texto propio en una región live —el
 * `<h1>` de la pantalla nueva no está todavía cuando esto corre, porque casi todas leen sus datos, y
 * una región live que se rellena con el título de la pantalla *anterior* es peor que ninguna. Lo que
 * cuenta la espera es el `role="status"` de `LoadingState`, que ya está.
 *
 * No se hace en el primer render: al abrir la aplicación el foco pertenece al documento, y robarlo
 * para ponerlo tras la barra se salta el salto al contenido antes de que a nadie le dé tiempo a
 * usarlo.
 */
export function useRouteFocus(): void {
  const { pathname } = useLocation();
  const isFirstRender = useRef(true);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }

    // `tabIndex={-1}` en el `<main>` es lo que lo hace enfocable, y de paso arregla el salto al
    // contenido: un ancla hacia un elemento no enfocable desplaza la página pero no lleva el foco,
    // así que el siguiente tabulador volvía a la barra que se acababa de saltar.
    document.getElementById(MAIN_CONTENT_ID)?.focus();
  }, [pathname]);
}
