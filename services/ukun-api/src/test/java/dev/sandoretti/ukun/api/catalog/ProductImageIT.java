package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * {@code PUT} and {@code DELETE /api/admin/products/{id}/image} (RF-10) — the only multipart operation
 * of the service, against a real storage directory.
 *
 * <p>A fixture of its own rather than one more subclass of {@code BackOfficeIntegrationTest}: this is
 * the one suite that needs {@code ukun.storage.local-root} pointing somewhere it may write, and a
 * property that changes the context has to be declared where the context is built.
 *
 * <p>The directory is a temporary one created per JVM and not the bind mount of {@code compose.yaml};
 * what these tests assert about files, they assert about files this suite wrote.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.servlet.multipart.max-file-size=" + ProductImageIT.MAX_FILE_SIZE})
class ProductImageIT extends AbstractIntegrationTest {

    /**
     * A limit of the suite's own, not the 5 MB of {@code application.yaml}, and the reason is the
     * protocol rather than the speed: Tomcat rejects an oversized upload the moment it sees it, while
     * the client is still writing the body, and a client that is cut off mid-write reports a broken
     * connection instead of reading the {@code 413}. With a small limit the whole request is already in
     * the socket buffers when the answer comes back, so what the test measures is the response and not
     * the timing.
     *
     * <p>The production number is asserted where it actually matters — {@code MultipartLimitTest}, which
     * checks it against the value the gateway enforces one layer earlier (defect #17).
     */
    static final String MAX_FILE_SIZE = "64KB";

    private static final long MAX_BYTES = 64L * 1024;

    private static final String IMAGE = "/api/admin/products/{id}/image";
    private static final UUID ADMIN_ACCOUNT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Created before the context, because the property that points at it is read while it starts. */
    private static final Path STORAGE_ROOT = createTemporaryStorageRoot();

    @DynamicPropertySource
    static void databaseAndStorage(DynamicPropertyRegistry registry) {
        useDatabase(registry, BACKOFFICE_DATABASE);
        useJwks(registry);
        registry.add("spring.liquibase.contexts", () -> "prod");
        registry.add("ukun.storage.local-root", STORAGE_ROOT::toString);
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedOneProduct() {
        ProductType tables = ProductMother.type("Mesas");

        inATransaction(() -> {
            entityManager.persist(tables);
            entityManager.persist(ProductMother.coffeeTable(tables));
        });
    }

    /**
     * The directory is emptied along with the table, and for the same reason: every assertion here
     * counts the files <em>this</em> test wrote, so anything an earlier one left would be indistinguishable
     * from a file the code under test failed to delete.
     */
    @AfterEach
    void clearTheCatalogueAndTheStorage() {
        jdbc.execute("TRUNCATE TABLE product, product_type RESTART IDENTITY CASCADE");

        try (var files = Files.list(STORAGE_ROOT)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not empty " + STORAGE_ROOT, e);
        }
    }

    /**
     * The response carries a servable URL and never {@code product.image_key}. The key stays inside the
     * service because it changes shape in v1.1, when the filesystem adapter becomes object storage.
     */
    @Test
    @DisplayName("an upload answers the servable URL, not the storage key")
    void storesAnImage() {
        upload(pngOf(64))
                .jsonPath("$.imageUrl")
                .value(url -> assertThat((String) url).startsWith("/media/products/").endsWith(".png"))
                .jsonPath("$.imageKey")
                .doesNotHaveJsonPath();

        assertThat(storedFiles()).hasSize(1);
    }

    /**
     * The extension of the key comes from the <strong>detected</strong> format, not from the name the
     * client sent — which is what keeps the type {@code MediaController} derives from that key honest.
     */
    @Test
    @DisplayName("the stored key is named after the real format, not after the uploaded filename")
    void namesTheKeyAfterTheContent() {
        upload(jpegOf(64), "anything.png");

        assertThat(imageUrlOf()).endsWith(".jpg");
    }

    /**
     * Replacement is an ordering: the new object is written and the row updated <em>before</em> the old
     * object is removed. The legacy deletes first and loses the photo when the save fails
     * ({@code docs/03-analisis-y-patrones.md} §3).
     */
    @Test
    @DisplayName("replacing leaves exactly one file behind, and it is the new one")
    void replacesTheImage() {
        upload(pngOf(64));
        String first = imageUrlOf();

        upload(jpegOf(64));
        String second = imageUrlOf();

        assertThat(second).isNotEqualTo(first).endsWith(".jpg");
        assertThat(storedFiles()).hasSize(1).allSatisfy(file -> assertThat(file).endsWith(".jpg"));
    }

    /**
     * A file whose name and declared type say "image" and whose content does not. This is the case the
     * extension check of the legacy cannot see, and the reason {@code docs/08-identidad-y-seguridad.md}
     * §9 asks for the magic number.
     */
    @Test
    @DisplayName("a .png that is not an image is a 415 listing what is accepted")
    void refusesAFileThatIsNotAnImage() {
        restTestClient
                .put()
                .uri(IMAGE, productIdOf("MESA-CAFE"))
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken(ADMIN_ACCOUNT)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart("#!/bin/sh\necho not an image\n".getBytes(), "innocent.png"))
                .exchange()
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNSUPPORTED_MEDIA_TYPE")
                .jsonPath("$.allowed")
                .isEqualTo(List.of("image/jpeg", "image/png", "image/webp"));

        assertThat(storedFiles()).isEmpty();
    }

    /**
     * Over {@code spring.servlet.multipart.max-file-size}, whichever value it holds. {@code maxBytes}
     * reports the configured limit rather than a constant, so the client knows what to retry under —
     * and so a limit changed in the configuration cannot leave the response advertising the old one.
     */
    @Test
    @DisplayName("an oversized upload is a 413 saying what the limit is")
    void refusesAnOversizedUpload() {
        restTestClient
                .put()
                .uri(IMAGE, productIdOf("MESA-CAFE"))
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken(ADMIN_ACCOUNT)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(pngOf((int) MAX_BYTES * 2), "huge.png"))
                .exchange()
                .expectStatus()
                .isEqualTo(413)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("PAYLOAD_TOO_LARGE")
                .jsonPath("$.maxBytes")
                .isEqualTo(MAX_BYTES);

        assertThat(storedFiles()).isEmpty();
    }

    @Test
    @DisplayName("deleting the image removes the file and leaves the product without one")
    void removesTheImage() {
        long id = productIdOf("MESA-CAFE");
        upload(pngOf(64));

        restTestClient
                .delete()
                .uri(IMAGE, id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken(ADMIN_ACCOUNT)))
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(storedFiles()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT image_key FROM product WHERE id = ?", String.class, id))
                .isNull();
    }

    /** Idempotent: the product seeded by the mother starts with a key but no file, and then with none. */
    @Test
    @DisplayName("deleting the image twice answers 204 both times")
    void removingTheImageIsIdempotent() {
        long id = productIdOf("MESA-CAFE");

        for (int attempt = 0; attempt < 2; attempt++) {
            restTestClient
                    .delete()
                    .uri(IMAGE, id)
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminToken(ADMIN_ACCOUNT)))
                    .exchange()
                    .expectStatus()
                    .isNoContent();
        }
    }

    @Test
    @DisplayName("an employee without the admin flag cannot upload")
    void refusesANonAdmin() {
        restTestClient
                .put()
                .uri(IMAGE, productIdOf("MESA-CAFE"))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(UUID.randomUUID())))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(pngOf(64), "photo.png"))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    private RestTestClient.BodyContentSpec upload(byte[] content) {
        return upload(content, "photo.png");
    }

    private RestTestClient.BodyContentSpec upload(byte[] content, String filename) {
        return restTestClient
                .put()
                .uri(IMAGE, productIdOf("MESA-CAFE"))
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken(ADMIN_ACCOUNT)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(content, filename))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();
    }

    private String imageUrlOf() {
        return "/media/products/"
                + jdbc.queryForObject("SELECT image_key FROM product WHERE sku = 'MESA-CAFE'", String.class);
    }

    private List<String> storedFiles() {
        try (var files = Files.list(STORAGE_ROOT)) {
            return files.map(path -> path.getFileName().toString()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + STORAGE_ROOT, e);
        }
    }

    private static MultiValueMap<String, HttpEntity<?>> multipart(byte[] content, String filename) {
        ByteArrayResource part = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("file", filename);
        headers.setContentType(MediaType.IMAGE_PNG);

        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(part, headers));

        return body;
    }

    /** A valid PNG header followed by filler: what the format check reads is the first eight bytes. */
    private static byte[] pngOf(int size) {
        return fileOf(size, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    private static byte[] jpegOf(int size) {
        return fileOf(size, 0xFF, 0xD8, 0xFF);
    }

    private static byte[] fileOf(int size, int... signature) {
        byte[] content = new byte[size];
        for (int i = 0; i < signature.length; i++) {
            content[i] = (byte) signature[i];
        }
        return content;
    }

    private static Path createTemporaryStorageRoot() {
        try {
            return Files.createTempDirectory("ukun-storage");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the storage root of the test", e);
        }
    }
}
