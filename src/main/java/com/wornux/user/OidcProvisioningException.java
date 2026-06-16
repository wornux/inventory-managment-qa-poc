package com.wornux.user;

public class OidcProvisioningException extends RuntimeException {

    public OidcProvisioningException(String message) {
        super(message);
    }

    public OidcProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
