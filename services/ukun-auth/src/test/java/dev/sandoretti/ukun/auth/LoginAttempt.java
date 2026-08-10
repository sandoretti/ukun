package dev.sandoretti.ukun.auth;

/**
 * The response to a form login attempt, reduced to what the assertions of this suite look at.
 *
 * <p>{@code location} rather than a status code is what says whether it worked: the default form
 * login answers a 302 either way and puts the outcome in the target, which is the shape
 * {@code docs/08-identidad-y-seguridad.md} §8 asks for — an endpoint that answered differently to a
 * wrong password and to an unknown email would be an account enumeration oracle.
 */
record LoginAttempt(int status, String location, String retryAfter, String sessionCookie) {

    boolean succeeded() {
        return location != null && !location.contains("error");
    }
}
