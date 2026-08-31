import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { Button } from 'react-bootstrap';

import { ErrorState } from './ErrorState';

type FallbackProps = { error: unknown };

/**
 * Lo que se pinta en lugar de la ruta que no llegó a montarse.
 *
 * El mensaje sale de `ErrorState` y no de una cadena propia: `toProblem` convierte cualquier cosa
 * que no sea un `ProblemDetail` en `INTERNAL_ERROR`, que es un `code` del registro cerrado y ya tiene
 * su texto en los dos idiomas. Un fallo de JavaScript no trae `code`, pero «algo ha fallado por
 * nuestra parte» es exactamente lo que hay que decir.
 *
 * Y una salida, porque sin ella esto sería una pantalla muerta con mejor aspecto que el blanco pero
 * igual de terminal. Recargar es la salida honesta: la causa típica es un módulo de la ruta que no se
 * pudo descargar, y eso se arregla volviéndolo a pedir.
 */
function RouteErrorFallback({ error }: FallbackProps) {
  const { t } = useTranslation('common');

  return (
    <div className="py-5">
      <ErrorState error={error} />
      <Button variant="primary" onClick={() => window.location.reload()}>
        {t('error.reload')}
      </Button>
    </div>
  );
}

type BoundaryProps = {
  /** Al cambiar, el borde se rearma. Es la ruta: irse a otra pantalla tiene que curar la rota. */
  resetKey: string;
  children: ReactNode;
};

type BoundaryState = { error: unknown };

class Boundary extends Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { error: null };

  static getDerivedStateFromError(error: unknown): BoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Se traga la pantalla, no el diagnóstico: sin esto el fallo desaparecería de la consola y con él
    // la única pista de qué módulo o qué componente reventó.
    console.error('[route] la pantalla no se pudo montar', error, info.componentStack);
  }

  componentDidUpdate(previous: BoundaryProps): void {
    if (previous.resetKey !== this.props.resetKey && this.state.error !== null) {
      this.setState({ error: null });
    }
  }

  render(): ReactNode {
    return this.state.error === null ? (
      this.props.children
    ) : (
      <RouteErrorFallback error={this.state.error} />
    );
  }
}

/**
 * El cuarto estado del cascarón: qué se ve cuando la ruta **no llega a montarse**.
 *
 * Sin esto, un error de render en cualquier pantalla deja la aplicación entera en blanco, porque
 * React desmonta el árbol cuando nadie recoge la excepción. No es teórico: lo encontró el CI de este
 * PR, con un `Failed to fetch dynamically imported module` de una ruta perezosa que dejó una página
 * completamente vacía — ni barra, ni rail, ni forma de salir. Es el mismo defecto que
 * `docs/07-frontend-ux-ui.md` §Estados prohíbe en cualquier otra parte, en su versión más grave: el
 * error no sustituye a una lista, sustituye a todo.
 *
 * **Va dentro del `<main>` y no envolviendo el cascarón**, y esa es la decisión que importa. Lo que
 * falla es una pantalla, así que lo que se sustituye es una pantalla: la barra, la navegación y el
 * pie siguen ahí, y con ellos la forma de irse a otro sitio. Un borde alrededor de todo convertiría
 * cada fallo de una ruta en la misma pantalla muerta que viene a evitar.
 *
 * Es una clase porque en React solo una clase puede recoger un error de render; el `resetKey` entra
 * desde aquí para que la parte que usa hooks siga siendo una función.
 */
export function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();

  return <Boundary resetKey={pathname}>{children}</Boundary>;
}
