package dev.sandoretti.ukun.api.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sandoretti.ukun.api.catalog.ImageBytes;
import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.infra.ProductRepository;
import dev.sandoretti.ukun.api.storage.FileStoragePort;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The <strong>ordering</strong> of the image replacement, which is the requirement of
 * {@code docs/08-identidad-y-seguridad.md} §9 and the defect the legacy has: {@code UploadFileServiceImpl}
 * deletes the previous photo before saving the new one, and loses it when the save fails
 * ({@code docs/03-analisis-y-patrones.md} §3).
 *
 * <p>A unit test with a mocked port, and the {@code testing} skill allows exactly this: {@code
 * FileStoragePort} is one of the three real external boundaries. The repository is mocked as well —
 * which that skill rules out when the point is to verify a query or a transaction, and this is neither:
 * what is under test is which call happens before which, and against a database the interesting case
 * (the write failing) could only be produced by breaking the filesystem.
 */
class ProductImageServiceTest {

    private static final Long PRODUCT_ID = 7L;
    private static final String PREVIOUS_KEY = "mesa-cafe.jpg";

    private static final byte[] PNG = ImageBytes.png(8);

    private final ProductRepository products = mock(ProductRepository.class);
    private final FileStoragePort storage = mock(FileStoragePort.class);

    private ProductImageService images;
    private Product product;

    @BeforeEach
    void createTheServiceOverAProductWithAPhoto() {
        CatalogProperties properties = new CatalogProperties();
        images = new ProductImageService(products, storage, new ProductImageUrls(properties));

        product = new Product();
        product.setId(PRODUCT_ID);
        product.setImageKey(PREVIOUS_KEY);

        when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * The old object is removed only once the new key is in the database. Any other order is the legacy
     * bug: between the two calls there is a failure mode, and it must be the one that leaves an orphaned
     * file rather than the one that leaves a product with no photo.
     */
    @Test
    @DisplayName("a failed write leaves the previous image exactly where it was")
    void doesNotDeleteTheOldImageWhenTheNewOneCannotBeWritten() {
        when(storage.store(any(), anyString())).thenThrow(new UncheckedIOException(new java.io.IOException("disk")));

        assertThatThrownBy(() -> images.replace(PRODUCT_ID, PNG)).isInstanceOf(UncheckedIOException.class);

        verify(storage, never()).delete(anyString());
        assertThat(product.getImageKey()).isEqualTo(PREVIOUS_KEY);
    }

    @Test
    @DisplayName("a successful replacement points the product at the new key and drops the old object")
    void replacesInTheRightOrder() {
        when(storage.store(any(), anyString())).thenReturn("new-key.png");

        ProductImageResponse response = images.replace(PRODUCT_ID, PNG);

        assertThat(product.getImageKey()).isEqualTo("new-key.png");
        assertThat(response.imageUrl()).isEqualTo("/media/products/new-key.png");
        verify(storage).delete(PREVIOUS_KEY);
    }

    /** Nothing is written before the file has been recognised: a rejected upload touches no storage. */
    @Test
    @DisplayName("a file that is not an image never reaches storage")
    void refusesBeforeStoringAnything() {
        assertThatThrownBy(() -> images.replace(PRODUCT_ID, "#!/bin/sh".getBytes()))
                .isInstanceOf(UnsupportedImageTypeException.class);

        verify(storage, never()).store(any(), anyString());
        verify(storage, never()).delete(anyString());
    }

    /** A product with no photo has nothing to compensate for, so nothing is deleted. */
    @Test
    @DisplayName("the first upload deletes no previous object")
    void doesNotDeleteWhenThereWasNoImage() {
        product.setImageKey(null);
        when(storage.store(any(), anyString())).thenReturn("first-key.png");

        images.replace(PRODUCT_ID, PNG);

        verify(storage, never()).delete(anyString());
    }
}
