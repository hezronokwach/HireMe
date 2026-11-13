package com.example.hireme.user;

/**
 * The demo accounts a portfolio deployment is seeded with (when {@code hireme.demo-listings.enabled=true}).
 * The demo owner can be signed into with one click from the login page; the demo client only exists so
 * the owner side has booking requests to look at, and nobody can sign in as it.
 */
public final class DemoAccounts {

    public static final String OWNER_EMAIL = "demo-owner@hireme.example";
    public static final String CLIENT_EMAIL = "demo-client@hireme.example";

    private DemoAccounts() {
    }
}
