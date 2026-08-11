package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.orders.domain.CustomerOrder;
import dev.sandoretti.ukun.api.orders.domain.OrderLine;
import dev.sandoretti.ukun.api.orders.domain.PaymentSnapshot;
import dev.sandoretti.ukun.api.orders.domain.ShippingAddress;
import org.mapstruct.Mapper;

/**
 * The order as the contract publishes it, with MapStruct like every mapping in this service
 * (ADR-0009). {@code ModelMapper} and {@code BeanUtils.copyProperties} stay banned.
 *
 * <p>Not one {@code @Mapping} in the whole interface, and that is a property of the design rather
 * than luck: the response records were named after the aggregate's own accessors, so every target
 * maps by name — including {@code productId}, which {@link OrderLine} exposes as a getter over its
 * composite key. With {@code unmappedTargetPolicy = ERROR} arriving through {@code compilerArgs}, a
 * field added to either side without a source stops the build instead of serialising as {@code null}.
 *
 * <p>It lives in {@code application/} and not in {@code web/} for the reason {@code ProductMapper}
 * gives: the generated implementation reads entities, and {@code ..web..} is the package the ArchUnit
 * rule "no class in a web package reaches for {@code jakarta.persistence}" watches.
 *
 * <p>There is no mapper for the checkout attempt. That response composes three modules — lines from
 * here, names and prices frozen from {@code catalog}, availability from {@code inventory} — so
 * {@code CheckoutService} builds it, the same call {@code CartService} makes for the cart.
 */
@Mapper
public interface OrderMapper {

    OrderResponse toResponse(CustomerOrder order);

    OrderAddressResponse toAddress(ShippingAddress address);

    OrderPaymentResponse toPayment(PaymentSnapshot payment);

    OrderLineResponse toLine(OrderLine line);
}
