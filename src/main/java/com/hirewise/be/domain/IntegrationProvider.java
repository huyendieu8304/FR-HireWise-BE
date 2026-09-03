package com.hirewise.be.domain;

import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;

/**
 * 3rd-party provider an {@link IntegrationConnection} talks to. Cloud
 * Storage providers (UC-07/UC-08: Google Drive, Dropbox) and Calendar
 * providers (UC-18: Google Calendar, Outlook Calendar via Microsoft Graph).
 */
public enum IntegrationProvider {
    GOOGLE_DRIVE,
    DROPBOX,
    /** UC-18: Google Calendar via the Google Calendar API v3. */
    GOOGLE_CALENDAR,
    /** UC-18: Outlook Calendar via the Microsoft Graph API. */
    OUTLOOK_CALENDAR;

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
