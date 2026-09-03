package com.hirewise.be.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the caller's IP address off a request, honouring
 * {@code X-Forwarded-For} so a deployment behind a proxy/load balancer does
 * not record the proxy's address for every visitor.
 * <p>
 * Shared by UC-01's per-IP login rate limit and UC-39, which stores the
 * signer's IP as part of the e-signature evidence
 * ({@code offer_signatures.ip_address}).
 * <p>
 * The header is client-supplied and therefore spoofable: it is good enough
 * for rate-limit bucketing and for an audit trail, but must never be treated
 * as an authentication or authorization input.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * @param request the incoming servlet request
     * @return the first hop in {@code X-Forwarded-For}, or the socket's own
     *         remote address when that header is absent
     */
    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // The header is a comma-separated chain; the original client is first.
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
