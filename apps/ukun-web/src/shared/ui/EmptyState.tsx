import React from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from './Icon';

type EmptyStateProps = {
  message?: string;
  icon?: string;
};

export const EmptyState: React.FC<EmptyStateProps> = ({ message, icon = 'inbox' }) => {
  const { t } = useTranslation('common');

  return (
    <div className="d-flex flex-column align-items-center justify-content-center p-5 text-muted text-center">
      <Icon name={icon} size="lg" className="mb-3 opacity-50" />
      <p className="mb-0">{message || t('empty')}</p>
    </div>
  );
};
