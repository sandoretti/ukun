import { useTranslation } from 'react-i18next';
import { Button } from 'react-bootstrap';

import { csrfToken } from '../../../shared/api/http';
import { forgetReturnTo } from '../../../shared/auth/signIn';
import { Icon } from '../../../shared/ui/Icon';

type SignOutFormProps = {
  /** Named variant, as everywhere else in the app — never a pile of booleans. */
  variant: 'primary' | 'outline-primary';
};

/**
 * Signing out, as a **form the browser submits**, never a call through axios.
 *
 * `POST /logout` ends in the gateway's `OidcClientInitiatedServerLogoutSuccessHandler`: the response
 * is a redirect to `ukun-auth`'s end session endpoint — another origin — and from there back to `/`.
 * An XHR cannot follow that, so the session at the authorization server would stay alive and
 * clicking "sign in" again would walk straight back in without a password. A real navigation
 * follows the whole chain, which is what actually ends the session.
 *
 * The CSRF token travels in the hidden field because there is no interceptor on a form submit. It is
 * the same string the axios interceptor sends in `X-XSRF-TOKEN`, read with the same function: the
 * gateway compares cookie and value literally (`ServerCsrfTokenRequestAttributeHandler`), so a
 * second reader that decoded it differently would answer 403 on the one action that has no screen
 * to show it on.
 */
export function SignOutForm({ variant }: SignOutFormProps) {
  const { t } = useTranslation('common');

  return (
    // `_csrf` is Spring Security's default parameter name, and `/logout` is the gateway's own
    // endpoint — reserved, so it is never a route of this router.
    <form method="post" action="/logout" onSubmit={forgetReturnTo}>
      <input type="hidden" name="_csrf" value={csrfToken() ?? ''} />
      <Button type="submit" variant={variant} size="sm" className="d-inline-flex align-items-center gap-2">
        <Icon name="logout" size="sm" />
        {t('account.signOut')}
      </Button>
    </form>
  );
}
