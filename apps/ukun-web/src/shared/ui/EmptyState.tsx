import { useTranslation } from 'react-i18next';

import { Icon } from './Icon';

type EmptyStateProps = {
  /** Why there is nothing, in the words of the screen. Falls back to the generic one of `common`. */
  message?: string;
  icon?: string;
};

/** A read that succeeded and came back with nothing: not an error, and never a blank area. */
export function EmptyState({ message, icon = 'inbox' }: EmptyStateProps) {
  const { t } = useTranslation('common');

  return (
    <div className="d-flex flex-column align-items-center justify-content-center p-5 text-muted text-center">
      <Icon name={icon} size="lg" className="mb-3 opacity-50" />
      <p className="mb-0">{message ?? t('empty')}</p>
    </div>
  );
}
