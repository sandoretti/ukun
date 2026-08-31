package dev.sandoretti.ukun.api;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where {@code docs/} is, for the two tests that check code against a document.
 *
 * <p>{@code docs/} lives outside this Gradle project, and the working directory of a test is the
 * project's own, so neither the contract of {@code OpenApiDriftIT} nor the event registry of
 * {@code AuditedEventRegistryTest} can be reached with a relative path that survives being run from
 * somewhere else. Walking up to {@code settings.gradle.kts} is the one landmark that is always there
 * and always at the root.
 */
public final class RepositoryRoot {

    private RepositoryRoot() {}

    public static Path resolve(String relativePath) {
        Path directory = Path.of("").toAbsolutePath();

        while (!Files.exists(directory.resolve("settings.gradle.kts"))) {
            directory = directory.getParent();
            if (directory == null) {
                throw new IllegalStateException("No settings.gradle.kts above " + Path.of("").toAbsolutePath());
            }
        }
        return directory.resolve(relativePath);
    }
}
