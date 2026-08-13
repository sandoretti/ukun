package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.infra.ProductRepository;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import dev.sandoretti.ukun.api.shared.observability.CurrentActor;
import dev.sandoretti.ukun.api.storage.FileStoragePort;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The photo of a product (RF-10): the only thing in this service that arrives as a file.
 *
 * <p>A service of its own rather than two more methods on {@link ProductAdminService}, because the
 * image is a sub-resource and not a field: field validation and file validation never share a handler,
 * which is what keeps the rest of the contract pure JSON and confines the hardening of
 * {@code docs/08-identidad-y-seguridad.md} §9 to one surface.
 *
 * <h2>Replacing is an ordering, and the ordering is the requirement</h2>
 *
 * Write the new object, point the row at it, and only then delete the old one. The legacy does it the
 * other way round — {@code UploadFileServiceImpl} deletes the previous photo before saving the new one
 * — so a failed save leaves the product with no image at all and nothing to recover it from
 * ({@code docs/03-analisis-y-patrones.md} §3). Reversing the two costs nothing and is the entire
 * difference between losing a photo and orphaning a file.
 *
 * <p>The compensation is deliberately asymmetric, and that follows from the same reasoning: a failed
 * <em>write</em> aborts the request, while a failed <em>deletion of the old object</em> is logged and
 * ignored. One of the two leaves the database pointing at bytes that do not exist; the other leaves
 * bytes nothing points at.
 */
@Service
@Transactional(readOnly = true)
public class ProductImageService {

    private static final String PRODUCT = "product";
    private static final String PRODUCT_ID = "productId";
    private static final String PREVIOUS_KEY = "previousKey";

    private static final String REPLACED_EVENT = "product.image.replaced";
    private static final String REMOVED_EVENT = "product.image.removed";

    private final ProductRepository products;
    private final FileStoragePort storage;
    private final ProductImageUrls imageUrls;

    ProductImageService(ProductRepository products, FileStoragePort storage, ProductImageUrls imageUrls) {
        this.products = products;
        this.storage = storage;
        this.imageUrls = imageUrls;
    }

    /**
     * Stores the uploaded file and points the product at it, replacing whatever was there.
     *
     * <p>The format is decided by the content of the file, and the extension of the generated key comes
     * from that same decision — so the type {@code MediaController} later derives from the key is the
     * type of the bytes it serves, not something a client asserted.
     *
     * <p>Size is not checked here: {@code spring.servlet.multipart.max-file-size} rejects an oversized
     * upload before this method is reached, and the gateway usually rejects it before the request even
     * arrives. Two layers with the same value, which is exactly what defect #17 lacked.
     *
     * @throws ResourceNotFoundException if no product carries that identifier
     * @throws UnsupportedImageTypeException if the file is not really a JPEG, a PNG or a WebP
     */
    @Transactional
    public ProductImageResponse replace(Long id, byte[] content) {
        Product product = require(id);
        ImageFormat format = ImageFormat.detect(content).orElseThrow(UnsupportedImageTypeException::new);

        String previousKey = product.getImageKey();
        String newKey = storage.store(content, format.extension());

        product.setImageKey(newKey);

        // Flushed, not merely saved: the old object is deleted next, and doing that before the new key
        // has actually reached the database would put the irreversible half of the operation first
        // again — the legacy's mistake with one more step in front of it.
        products.saveAndFlush(product);

        if (previousKey != null) {
            storage.delete(previousKey);
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, id);
        fields.put(PREVIOUS_KEY, previousKey);
        fields.put("newKey", newKey);
        BusinessEvents.emit(Level.INFO, REPLACED_EVENT, fields);

        return new ProductImageResponse(imageUrls.toImageUrl(newKey));
    }

    /**
     * Removes the photo: the object goes, {@code image_key} becomes {@code null}, and the product stays
     * in the catalogue without one — a valid state, since the photo is optional.
     *
     * <p>Idempotent: a product with no image answers {@code 204} too, and emits nothing, because nothing
     * happened.
     *
     * @throws ResourceNotFoundException if no product carries that identifier
     */
    @Transactional
    public void remove(Long id) {
        Product product = require(id);
        String previousKey = product.getImageKey();

        if (previousKey == null) {
            return;
        }

        product.setImageKey(null);
        products.saveAndFlush(product);
        storage.delete(previousKey);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(PRODUCT_ID, id);
        fields.put(PREVIOUS_KEY, previousKey);
        fields.put("adminId", CurrentActor.accountId());
        BusinessEvents.emit(Level.INFO, REMOVED_EVENT, fields);
    }

    private Product require(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id));
    }
}
