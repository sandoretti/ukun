package dev.sandoretti.ukun.gateway.web;

/**
 * The one exchange attribute the access log reads, named in one place because it is written from two
 * and read from a third.
 *
 * <p>It exists because {@link AccessLogWebFilter} runs in front of the security chain — so that a
 * request the chain refuses still gets a line — while the caller's identity is only knowable from
 * inside it. The attribute is how the second half reaches the first: same {@code ServerWebExchange}
 * on both sides of the chain, no thread affinity, no Reactor context to propagate upwards.
 *
 * <p>Its two writers are the two places where a caller is known: {@link AccountIdAttributeWebFilter}
 * for a request that gets through, and {@code ProblemDetailSecurityHandler} for one denied a role.
 */
public final class AccessLogAttributes {

    /** Qualified with the package so it cannot collide with an attribute of Spring Cloud Gateway. */
    public static final String ACCOUNT_ID = AccessLogAttributes.class.getName() + ".accountId";

    private AccessLogAttributes() {}
}
