import React from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from 'react-bootstrap';
import { Icon } from './Icon';

type ErrorStateProps = {
  code?: string;
  extensions?: Record<string, unknown>;
};

export const ErrorState: React.FC<ErrorStateProps> = ({ code, extensions }) => {
  const { t } = useTranslation(['errors', 'common']);

  // Si hay code, lo busca en errors.json, sino usa un genérico de common.json
  const errorMessage = code ? t(`errors:${code}`, extensions) : t('common:error');

  return (
    <Alert variant="danger" className="d-flex align-items-center">
      <Icon name="error" className="me-2" />
      <div>{errorMessage}</div>
    </Alert>
  );
};
