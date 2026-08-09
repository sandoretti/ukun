package dev.sandoretti.ukun.api.storage.application;

import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration of the {@code storage} module. It exists as configuration and not as a constant
 * for the reason {@code docs/03-analisis-y-patrones.md} §2 gives when it fixes defect #6: the legacy
 * carries {@code "/opt/resources/uploads/"} hardcoded in the middle of {@code UploadFileServiceImpl}.
 *
 * <p>{@code localRoot} is a deployment fact — the container side of the bind mount declared in
 * {@code compose.yaml}. It disappears in v1.1 along with the filesystem adapter, when object storage
 * replaces it and the configuration becomes a bucket instead of a directory.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "ukun.storage")
public class StorageProperties {

    private Path localRoot = Path.of("/opt/resources/uploads");
}
