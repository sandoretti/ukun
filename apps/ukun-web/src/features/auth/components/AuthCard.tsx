import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

type AuthCardProps = {
  /** El `<h1>` de la pantalla: este marco no tiene barra que lleve uno. */
  title: string;
  /** La línea bajo el título, que dice de qué va lo que se pide debajo. */
  lead: string;
  /**
   * La medida de la tarjeta. `reading` es una columna de lectura corta, para una pantalla de una
   * sola acción; `form` es la del formulario largo, la misma que `.settings-form`.
   *
   * Variante con nombre y no un `wide` booleano (`docs/07-frontend-ux-ui.md` §Composición): lo que
   * cambia es para qué sirve la tarjeta, no cuántos píxeles mide.
   */
  measure?: 'reading' | 'form';
  /** Lo que va **encima** de la marca: hoy, el aviso de por qué se está enseñando esta pantalla. */
  notice?: ReactNode;
  children: ReactNode;
};

/**
 * El marco de las dos pantallas de `AuthLayout`: marca, titular, entradilla y la salida.
 *
 * Existe porque son dos y tienen que seguir siendo la misma tarjeta. Lo que comparten no es solo el
 * borde —eso lo daría `.auth-card` a secas— sino dos decisiones que se pierden al copiar y pegar: la
 * marca va en tipografía y no como logotipo (el que se sirve es la silueta blanca, pensada para el
 * verde de la barra, y sobre la tarjeta no se vería), y **siempre hay vuelta a la tienda**, porque
 * este layout no tiene navegación y una pantalla sin salida es una trampa para quien llegó aquí por
 * un 401 sin pedirlo.
 *
 * El aviso es una ranura y no un hijo más porque su sitio es antes de la marca: quien fue expulsado
 * necesita leer por qué está aquí antes que cualquier otra cosa.
 */
export function AuthCard({ title, lead, measure = 'reading', notice, children }: AuthCardProps) {
  const { t } = useTranslation('common');

  return (
    <div className={measure === 'form' ? 'auth-card auth-card--wide' : 'auth-card'}>
      {notice}

      <p className="auth-card__brand">{t('title')}</p>

      <h1 className="h3 mb-2">{title}</h1>
      <p className="text-body-secondary mb-4">{lead}</p>

      {children}

      <p className="mt-4 mb-0">
        <Link to="/" className="small">
          {t('account.backHome')}
        </Link>
      </p>
    </div>
  );
}
