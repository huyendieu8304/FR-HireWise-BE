package com.hirewise.be.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

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

    /** Longest textual IP: IPv4-mapped IPv6, e.g. {@code 0:0:0:0:0:ffff:255.255.255.255}. */
    private static final int MAX_IP_LENGTH = 45;

    private static final Pattern IPV4 = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");

    /** Deliberately a charset check, not a full IPv6 grammar - see {@link #resolveIpLiteral}. */
    private static final Pattern IPV6_SHAPED = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private ClientIpResolver() {
    }

    /**
     * The caller's address exactly as reported, with no validation.
     * <p>
     * Used where any stable string is enough - notably per-IP rate-limit
     * bucketing, which must key on whatever the client presents rather than
     * discarding values it cannot parse.
     *
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

    /**
     * The caller's address as a validated IP literal, or {@code null} when it
     * is absent or not one.
     * <p>
     * Use this for anything persisted. {@code X-Forwarded-For} is attacker-
     * controlled, so a value taken straight from it can be arbitrary text of
     * arbitrary length - which would either overflow the column or, on a
     * stricter column type, fail the insert outright. In UC-39 that insert
     * is inside the signing transaction, so a junk header would stop a
     * candidate accepting their job. Audit metadata is never worth that:
     * an unparseable address is dropped rather than allowed to fail the write.
     *
     * @param request the incoming servlet request
     * @return a validated IPv4/IPv6 literal, or {@code null}
     */
    public static String resolveIpLiteral(HttpServletRequest request) {
        return toIpLiteral(resolve(request));
    }

    /**
     * Validates one address string.
     * <p>
     * IPv6 is checked by character set and length rather than by full
     * grammar: the goal is to keep junk and oversized input out of the
     * database, not to reimplement an address parser. {@code InetAddress} is
     * deliberately not used - it performs a DNS lookup for anything that is
     * not a literal, which would let a crafted header turn a signature into
     * a blocking network call.
     *
     * @param candidate raw address text; may be {@code null}
     * @return the trimmed literal, or {@code null} if it is not a plausible IP
     */
    static String toIpLiteral(String candidate) {
        if (candidate == null) {
            return null;
        }
        String value = candidate.trim();
        if (value.isEmpty()) {
            return null;
        }
        // "[::1]:8080" / "[::1]" - bracketed IPv6, optionally with a port.
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) {
                return null;
            }
            value = value.substring(1, close);
        } else if (value.indexOf(':') >= 0
                && value.indexOf(':') == value.lastIndexOf(':')
                && value.indexOf('.') >= 0) {
            // Exactly one colon alongside dots means "1.2.3.4:5678", not IPv6.
            value = value.substring(0, value.indexOf(':'));
        }

        if (value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return null;
        }
        if (IPV4.matcher(value).matches()) {
            for (String octet : value.split("\\.")) {
                if (Integer.parseInt(octet) > 255) {
                    return null;
                }
            }
            return value;
        }
        if (value.indexOf(':') >= 0 && IPV6_SHAPED.matcher(value).matches()) {
            return value;
        }
        return null;
    }
}
