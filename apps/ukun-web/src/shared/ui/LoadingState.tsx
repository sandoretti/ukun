import React from 'react';
import { useTranslation } from 'react-i18next';
import { Spinner } from 'react-bootstrap';

export const LoadingState: React.FC = () => {
  const { t } = useTranslation('common');

  return (
    <div className="d-flex justify-content-center align-items-center p-5" role="status" aria-label={t('loading')}>
      <Spinner animation="border" variant="primary" />
      <span className="visually-hidden">{t('loading')}</span>
    </div>
  );
};
