import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Alert, Col, Container, Row } from 'react-bootstrap';

import { ADMIN_EMPLOYEES_PATH } from '../../../shared/routes';
import { Icon } from '../../../shared/ui/Icon';
import { EmploymentBoundary } from '../components/EmploymentBoundary';
import { NewEmployeeForm } from './components/NewEmployeeForm';

/**
 * Adding an employee to the admin's store (RF-11) at `/admin/employees/new`.
 *
 * **The store is not asked for and cannot be chosen**: the new employee joins the store of the admin
 * creating them, resolved server-side from their `account_id` (`docs/08-identidad-y-seguridad.md`
 * §3). Its name is on screen so the admin knows where they are writing, and that is the whole of the
 * store's presence here.
 *
 * The alta reuses the signup saga, so it has **two successful answers and they are different news**:
 * a `201` lands on the listing filtered by the new email, where the row is (the form does that); a
 * `202` means the account exists but its activation was not confirmed, so signing in may not work
 * yet — and that is said here, in those words, with the listing offered rather than taken. Repeating
 * the same alta finishes the saga, which is why the text says to try again if the login refuses.
 */
export default function NewEmployeePage() {
  const { t } = useTranslation('backoffice');
  const [pendingActivation, setPendingActivation] = useState(false);

  return (
    <Container className="py-5">
      <EmploymentBoundary>
        {(employment) => (
          <Row>
            <Col lg={9} xl={7}>
              <Link
                to={ADMIN_EMPLOYEES_PATH}
                className="d-inline-flex align-items-center gap-1 mb-3"
              >
                <Icon name="arrow_back" size="sm" />
                {t('employees.new.back')}
              </Link>

              <h1 className="h2 mb-2">{t('employees.new.title')}</h1>
              <p className="text-body-secondary mb-4">
                {t('employees.new.lead', { store: employment.storeName })}
              </p>

              {pendingActivation ? (
                // `role="alert"` de `Alert`: es el desenlace de lo que se acaba de enviar, y quien no
                // ve la pantalla tiene que enterarse sin ir a buscarlo.
                <Alert variant="warning">
                  <h2 className="h6">{t('employees.new.pending.title')}</h2>
                  <p>{t('employees.new.pending.body')}</p>
                  <Link to={ADMIN_EMPLOYEES_PATH} className="alert-link">
                    {t('employees.new.pending.link')}
                  </Link>
                </Alert>
              ) : (
                <NewEmployeeForm
                  storeName={employment.storeName}
                  onActivationPending={() => setPendingActivation(true)}
                />
              )}
            </Col>
          </Row>
        )}
      </EmploymentBoundary>
    </Container>
  );
}
