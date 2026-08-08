package dev.sandoretti.ukun.api.shared.web;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Serialises a {@code BigDecimal} amount as the decimal <strong>string</strong> the contract's
 * {@code Money} schema declares, never as a JSON number.
 *
 * <p>This is not a formatting preference. The legacy system maps {@code DECIMAL(10,2)} onto
 * {@code Float} and loses cents (defect #5); sending the amount as a JSON number would hand the same
 * problem to the frontend, where it would be parsed into an IEEE-754 double. The backend emits the
 * digits unformatted and React formats them with {@code Intl.NumberFormat} (ADR-0019) — separators
 * and currency symbols are a presentation decision, and the browser knows the user's locale.
 *
 * <p>An annotation of our own rather than a global Jackson setting: writing <em>every</em> number as
 * a string would turn identifiers, quantities and page counts into strings too, which is not what the
 * contract says.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@JsonFormat(shape = JsonFormat.Shape.STRING)
public @interface Money {}
