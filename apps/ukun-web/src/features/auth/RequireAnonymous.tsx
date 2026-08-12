import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

import { useAuth } from '../../shared/hooks/useAuth';
import { LoadingState } from '../../shared/ui/LoadingState';

type RequireAnonymousProps = {
  children: ReactNode;
};

/**
 * El espejo de `RequireAuth`: una ruta que solo tiene sentido para quien **no** tiene sesión.
 *
 * Las dos pantallas del marco de entrada son así —identificarse y darse de alta—, y a quien ya está
 * dentro no se le enseña ninguna: una ofrecería una sesión que ya tiene y la otra un alta que solo
 * podría contestar «ese correo ya está en uso».
 *
 * Va a la portada y no restaura ningún destino, a diferencia de `RequireAuth`: `useRestoreReturnTo`
 * vive en `AppLayout` y es el mismo aterrizaje que tiene la vuelta del servidor de autorización. Un
 * mecanismo, no dos.
 *
 * `Navigate` y no un efecto con `navigate(...)`: aquí no hay nada que recordar antes de irse, que es
 * lo único que obligaba a `RequireAuth` a usar un efecto.
 */
export function RequireAnonymous({ children }: RequireAnonymousProps) {
  const { isAuthenticated, isPending } = useAuth();

  // La espera de la sonda de sesión, que no es un estado del que estas pantallas tengan nada que
  // decir.
  if (isPending) {
    return <LoadingState />;
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
