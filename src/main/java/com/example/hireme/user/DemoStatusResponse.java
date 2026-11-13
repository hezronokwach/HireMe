package com.example.hireme.user;

/**
 * Whether this server has the demo owner, so the login page knows whether to offer "Sign in as the demo owner".
 *
 * @param ownerAvailable true when demo data is on and the demo owner exists
 * @param ownerEmail     the demo owner's email, when available
 */
public record DemoStatusResponse(boolean ownerAvailable, String ownerEmail) {
}
