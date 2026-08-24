package com.hirewise.be.domain;

import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;

/**
 * 3rd-party provider an {@link IntegrationConnection} talks to. Only the
 * Cloud Storage providers needed through UC-08 are listed here; Calendar/
 * Social providers (UC-18/UC-19) extend this enum when those use cases are
 * implemented.
 */
public enum IntegrationProvider {
    GOOGLE_DRIVE,
    DROPBOX

    /**
     * Parses the kebab-case path segment used in
     * {@code /api/integrations/cloud-storage/{provider}/...} URLs (e.g.
     * {@code "google-drive"}) back into its enum constant.
     *
     * @param pathSegment the {@code {provider}} path variable value
     * @return the matching provider
     * @throws BadRequestException if {@code pathSegment} doesn't match a known provider
     */
    public static IntegrationProvider fromPathSegment(String pathSegment) {
        if (pathSegment != null) {
            for (IntegrationProvider provider : values()) {
                if (provider.toPathSegment().equals(pathSegment)) {
                    return provider;
                }
            }
        }
        throw new BadRequestException(ErrorCode.INTEGRATION_PROVIDER_UNSUPPORTED, pathSegment);
    }

    /** @return the kebab-case path segment for this provider, e.g. {@code "google-drive"}. */
    public String toPathSegment() {
        return name().toLowerCase().replace('_', '-');
    }
}
