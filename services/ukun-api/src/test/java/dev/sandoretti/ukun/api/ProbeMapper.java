package dev.sandoretti.ukun.api;

import org.mapstruct.Mapper;

/**
 * Exists only to prove the annotation processor chain works (see {@link StackValidationIT}).
 * {@code componentModel} and {@code unmappedTargetPolicy} are not set here: the root pom.xml
 * passes them to every module as compiler arguments.
 */
@Mapper
public interface ProbeMapper {

    ProbeView toView(Probe probe);
}
