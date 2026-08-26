import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Form } from 'react-bootstrap';

import { ErrorState } from '../../../shared/ui/ErrorState';
import { Fieldset } from '../../../shared/ui/Fieldset';
import { Icon } from '../../../shared/ui/Icon';
import { ProductImage } from '../../../shared/ui/ProductImage';
import type { AdminProduct } from '../api/adminProductKeys';
import { useDeleteProductImage, useUploadProductImage } from '../api/adminProductQueries';

/** What the contract accepts (`uploadProductImage`), and what the picker offers by default. */
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** Five megabytes, as the contract counts them. */
const MAX_BYTES = 5 * 1024 * 1024;

/** Why a file was refused before it was ever sent, or `null` when it was not. */
type LocalRejection = 'type' | 'size';

function rejectionOf(file: File): LocalRejection | null {
  if (!ACCEPTED_TYPES.includes(file.type)) {
    return 'type';
  }
  if (file.size > MAX_BYTES) {
    return 'size';
  }
  return null;
}

/** A file the reader has picked and the browser is drawing, before anything has been sent. */
type Selection = {
  file: File;
  previewUrl: string;
};

type ProductImagePanelProps = {
  product: AdminProduct;
};

/**
 * The photo of a product: what it has now, what is about to replace it, and the two operations that
 * do it.
 *
 * **The photo is not a field of the form** (RF-10). It is optional, it is uploaded against its own
 * sub-resource, and neither creating nor editing a product carries it — which is why this panel sits
 * next to the form instead of inside it, with its own button and its own outcome.
 *
 * The file is checked here for type and size before anything is sent, so the obvious mistakes cost a
 * sentence instead of a round trip. **That check is a courtesy, not the rule**: the server validates
 * the real content of the file rather than its extension or the type the browser guessed
 * (`docs/08-identidad-y-seguridad.md` §9), so its `415` and `413` are painted like any other answer.
 *
 * The preview is the browser's own object URL and is revoked when it stops being shown — a preview
 * that is never revoked is a file kept alive in memory for as long as the tab is open.
 */
export function ProductImagePanel({ product }: ProductImagePanelProps) {
  const { t } = useTranslation('backoffice');
  const upload = useUploadProductImage();
  const remove = useDeleteProductImage();

  const inputRef = useRef<HTMLInputElement>(null);
  /** The accepted file together with the URL it is being drawn from — they are born and die together. */
  const [selection, setSelection] = useState<Selection | null>(null);
  const [rejection, setRejection] = useState<LocalRejection | null>(null);

  // The only effect here, and it does nothing but clean up: the URL is created in the event that
  // picked the file, and released when that selection stops being the current one — replaced, cleared,
  // or the panel unmounted. An object URL nobody revokes keeps its file alive for as long as the tab.
  useEffect(() => {
    if (selection === null) {
      return;
    }

    return () => URL.revokeObjectURL(selection.previewUrl);
  }, [selection]);

  const clearSelection = () => {
    setSelection(null);
    setRejection(null);
    if (inputRef.current !== null) {
      // The control keeps the file name until it is told otherwise, and a picker still naming a file
      // that is no longer going anywhere is the one thing this panel must not show.
      inputRef.current.value = '';
    }
  };

  const onPick = (event: ChangeEvent<HTMLInputElement>) => {
    const picked = event.target.files?.[0] ?? null;

    upload.reset();
    remove.reset();

    if (picked === null) {
      clearSelection();
      return;
    }

    const refused = rejectionOf(picked);
    setRejection(refused);
    setSelection(
      refused === null ? { file: picked, previewUrl: URL.createObjectURL(picked) } : null,
    );
  };

  const onUpload = () => {
    if (selection !== null) {
      upload.mutate({ id: product.id, file: selection.file }, { onSuccess: clearSelection });
    }
  };

  const isBusy = upload.isPending || remove.isPending;

  return (
    <Fieldset legend={t('products.image.title')} description={t('products.image.description')}>
      <div className="d-flex flex-wrap align-items-start gap-4 mb-3">
        <figure className="mb-0">
          <figcaption className="form-label d-block mb-1">{t('products.image.current')}</figcaption>
          <ProductImage
            imageUrl={product.imageUrl}
            alt={t('products.image.alt', { product: product.name })}
            className="admin-product-photo"
            fallback={<Icon name="image" size="lg" />}
          />
        </figure>

        {selection !== null && (
          <figure className="mb-0">
            <figcaption className="form-label d-block mb-1">{t('products.image.preview')}</figcaption>
            {/* The picked file, drawn by the browser before anything has been sent: what "preview"
                means here is that nothing has happened yet. */}
            <img src={selection.previewUrl} alt={t('products.image.previewAlt')} className="admin-product-photo" />
          </figure>
        )}
      </div>

      <Form.Group className="mb-3">
        <Form.Label htmlFor="product-image-file">{t('products.image.pick')}</Form.Label>
        <Form.Control
          id="product-image-file"
          ref={inputRef}
          type="file"
          accept={ACCEPTED_TYPES.join(',')}
          onChange={onPick}
          disabled={isBusy}
        />
        <Form.Text className="d-block">{t('products.image.rules')}</Form.Text>
      </Form.Group>

      <div aria-live="polite">
        {rejection !== null && <Alert variant="warning">{t(`products.image.rejected.${rejection}`)}</Alert>}
        {upload.isError && <ErrorState error={upload.error} />}
        {remove.isError && <ErrorState error={remove.error} />}
      </div>

      <div className="d-flex flex-wrap gap-2">
        <Button variant="primary" onClick={onUpload} disabled={selection === null || isBusy}>
          {t('products.image.upload')}
        </Button>
        {product.imageUrl !== null && (
          <Button
            variant="outline-secondary"
            onClick={() => remove.mutate(product.id)}
            disabled={isBusy}
          >
            {t('products.image.remove')}
          </Button>
        )}
      </div>
    </Fieldset>
  );
}
