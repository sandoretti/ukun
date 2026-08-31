import { Icon } from './Icon';

type FailureNoteProps = {
  /** Qué se ha perdido, en las palabras de la pantalla. Ya traducido: `shared/ui` no conoce claves. */
  message: string;
  /** Un icono del mapa de `docs/07-frontend-ux-ui.md` §Iconografía, cuando el aviso lo pide. */
  icon?: string;
  /** Solo la separación con lo que tiene alrededor, que la decide quien lo coloca. */
  className?: string;
};

/**
 * Un fallo que **no** sustituye a la pantalla: se cuenta al lado de lo que sigue estando.
 *
 * Es la otra mitad de `ErrorState`, y la distinción es la regla no negociable de
 * `docs/07-frontend-ux-ui.md` §Estados — el error toma la pantalla solo cuando no hay nada que
 * enseñar. `ErrorState` es un `Alert` que ocupa el sitio y traduce el `code` del `ProblemDetail`,
 * porque ahí el fallo *es* la pantalla; esto es una línea discreta y anunciada, porque lo que hay
 * delante sigue sirviendo y lo que se ha perdido es un pedazo.
 *
 * Existe porque ese pedazo lo estaban escribiendo cuatro sitios por su cuenta, con el mismo
 * `role="status"` y el mismo gris pequeño: el bloque siguiente del catálogo que no llega
 * (`CatalogLoadMore`), la disponibilidad que no contesta (`CatalogToolbar`), el panel de filtros
 * que se queda a medias (`CatalogPage`) y la recarga de un listado que falla (`QueryBoundary`). Lo
 * que se unifica no es el margen, es la decisión: un fallo secundario se anuncia con `role="status"`
 * —cortés, sin interrumpir— y nunca con la presencia de una alerta.
 *
 * El texto llega resuelto y no como `code`, a diferencia de `ErrorState`: aquí lo útil no es qué
 * salió mal por dentro sino qué falta en la pantalla, y eso solo lo sabe quien la escribió.
 */
export function FailureNote({ message, icon, className = '' }: FailureNoteProps) {
  return (
    <p
      role="status"
      className={`small text-body-secondary mb-0 ${icon === undefined ? '' : 'd-flex align-items-center gap-1'} ${className}`.trim()}
    >
      {icon !== undefined && <Icon name={icon} size="sm" />}
      {message}
    </p>
  );
}
