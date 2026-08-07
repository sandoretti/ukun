import { useTranslation } from 'react-i18next';
import { useLocaleSync } from '../shared/hooks/useLocaleSync';

function App() {
  const { t } = useTranslation('common');
  useLocaleSync();

  return (
    <div className="container mt-4">
      <header>
        <h1>{t('title')}</h1>
      </header>
      <main>
        <p>{t('loading')}</p>
      </main>
    </div>
  );
}

export default App;
