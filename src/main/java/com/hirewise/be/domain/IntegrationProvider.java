package com.hirewise.be.domain;

/**
 * 3rd-party provider an {@link IntegrationConnection} talks to. Only the
 * Cloud Storage providers needed through UC-08 are listed here; Calendar/
 * Social providers (UC-18/UC-19) extend this enum when those use cases are
 * implemented.
 */
public enum IntegrationProvider {
    GOOGLE_DRIVE,
    DROPBOX
}
