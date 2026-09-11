package com.hirewise.be.integration;

import com.hirewise.be.domain.IntegrationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google Calendar side of UC-18 (OAuth 2.0 authorization code flow + Calendar
 * API health probe). Uses the Google Calendar API v3.
 *
 * <p>Scope: {@code calendar.readonly} — the minimum scope that lets the
 * app verify the connection is live without modifying the user's calendar,
 * satisfying the least-privilege principle of BR-INTEG-01. Write scopes
 * (needed by UC-24/25/26 to create interview events) can be added later
 * by updating the scope constant below.</p>
 */
@Slf4j
@Component
public class GoogleCalendarProviderClient implements CalendarProviderClient {

    private static final String AUTHORIZATION_ENDPOINT =
            "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT =
            "https://oauth2.googleapis.com/token";
    private static final String CALENDAR_API_BASE_URL =
            "https://www.googleapis.com/calendar/v3";
    /**
     * Scope for UC-18 Test Connection and UC-24 interview event/meet creation.
     */
    private static final String SCOPE =
            "https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/calendar.readonly";


    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient tokenClient = RestClient.create();
    private final RestClient calendarClient =
            RestClient.builder().baseUrl(CALENDAR_API_BASE_URL).build();

    public GoogleCalendarProviderClient(
            @Value("${app.integration.google-calendar.client-id:}") String clientId,
            @Value("${app.integration.google-calendar.client-secret:}") String clientSecret,
            @Value("${app.integration.google-calendar.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.GOOGLE_CALENDAR;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@code access_type=offline} and {@code prompt=consent} to ensure a
     * refresh token is always returned (same reasoning as
     * {@code GoogleDriveProviderClient#buildAuthorizationUrl}).</p>
     */
    @Override
    public String buildAuthorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                // access_type=offline + prompt=consent ensures a refresh_token
                // is always issued, including on Reconnect (UC-18 AF-01).
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeAuthorizationCode(String code) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException(
                    "Google Calendar token exchange failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Google normally does not reissue a refresh_token on every refresh call
     * (only when {@code prompt=consent} is used again). Callers must keep using
     * the same refresh token unless the response explicitly contains a new one.</p>
     */
    @Override
    public OAuthTokenResponse refreshAccessToken(String refreshToken) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException(
                    "Google Calendar token refresh failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Makes a cheap read-only call to {@code GET /calendars/primary} —
     * returns 200 if the token is valid and the Calendar API is reachable.</p>
     */
    @Override
    public void testConnection(String accessToken) {
        try {
            log.info("Google Calendar test connection starting");
            calendarClient.get()
                    .uri("/calendars/primary")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Google Calendar test connection succeeded");
        } catch (RestClientException e) {
            throw new IntegrationConnectException(
                    "Google Calendar API probe failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Google Calendar event with a Google Meet conference link.
     */
    public String createMeetingEvent(
            String accessToken,
            String summary,
            String description,
            java.time.LocalDateTime startDateTime,
            java.time.LocalDateTime endDateTime) {
        return createMeetingEvent(accessToken, summary, description, startDateTime, endDateTime, java.util.List.of());
    }

    /**
     * Creates a Google Calendar event with a Google Meet conference link and invites attendees,
     * automatically syncing the event to their personal Google Calendars.
     */
    public String createMeetingEvent(
            String accessToken,
            String summary,
            String description,
            java.time.LocalDateTime startDateTime,
            java.time.LocalDateTime endDateTime,
            java.util.List<String> attendeeEmails) {
        try {
            String startIso = startDateTime.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endIso = endDateTime.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("summary", summary);
            body.put("description", description != null ? description : "");
            body.put("start", java.util.Map.of("dateTime", startIso, "timeZone", "Asia/Ho_Chi_Minh"));
            body.put("end", java.util.Map.of("dateTime", endIso, "timeZone", "Asia/Ho_Chi_Minh"));
            body.put("conferenceData", java.util.Map.of(
                    "createRequest", java.util.Map.of(
                            "requestId", java.util.UUID.randomUUID().toString(),
                            "conferenceSolutionKey", java.util.Map.of("type", "hangoutsMeet")
                    )
            ));

            if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
                java.util.List<java.util.Map<String, String>> attendees = attendeeEmails.stream()
                        .filter(email -> email != null && !email.isBlank())
                        .distinct()
                        .map(email -> java.util.Map.of("email", email))
                        .toList();
                if (!attendees.isEmpty()) {
                    body.put("attendees", attendees);
                }
            }

            String uri = (attendeeEmails != null && !attendeeEmails.isEmpty())
                    ? "/calendars/primary/events?conferenceDataVersion=1&sendUpdates=all"
                    : "/calendars/primary/events?conferenceDataVersion=1";

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = calendarClient.post()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(java.util.Map.class);

            if (response != null && response.get("hangoutLink") != null) {
                return String.valueOf(response.get("hangoutLink"));
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to create Google Calendar event with Meet conference: {}", e.getMessage());
            return null;
        }
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw new IntegrationConnectException(
                    "Google Calendar integration is not configured"
                    + " (app.integration.google-calendar.client-id/client-secret)");
        }
    }
}
