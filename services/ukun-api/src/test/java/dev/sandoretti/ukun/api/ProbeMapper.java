package dev.sandoretti.ukun.api;

import org.mapstruct.Mapper;

/**
 * Exists only to prove the annotation processor chain works (see {@link StackValidationIT}).
 * {@code componentModel} and {@code unmappedTargetPolicy} are not set here: this service's
 * {@code build.gradle.kts} passes them as compiler arguments, to main and test sources alike.
 */
@Mapper
public interface ProbeMapper {

    ProbeView toView(Probe probe);
}
