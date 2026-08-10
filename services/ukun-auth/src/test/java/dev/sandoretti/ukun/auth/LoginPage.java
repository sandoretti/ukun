package dev.sandoretti.ukun.auth;

/** The two things a browser takes away from the login page before it can post credentials back. */
record LoginPage(String csrfToken, String sessionCookie) {}
