package dev.sandoretti.ukun.archunit.compliant.web;

import dev.sandoretti.ukun.archunit.compliant.CompliantEntity;
import dev.sandoretti.ukun.archunit.compliant.CompliantService;
import dev.sandoretti.ukun.archunit.compliant.CompliantService.ProductView;
import org.springframework.web.bind.annotation.RestController;

/**
 * A controller in a {@code web} package that depends on a service and knows nothing about JPA — the
 * arrangement the first and third rules describe from the other side.
 */
@RestController
public class CompliantController {

    private final CompliantService service;

    public CompliantController(CompliantService service) {
        this.service = service;
    }

    public ProductView find(CompliantEntity entity) {
        return service.find(entity);
    }
}
