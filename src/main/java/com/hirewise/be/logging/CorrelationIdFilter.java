package com.hirewise.be.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Attaches a correlation id to every incoming request so all log lines produced
 * while handling that request can be traced back to the same id.
 * <p>
 * The id is read from the "X-Correlation-ID" request header when the client sends
 * one; otherwise a new random UUID is generated. The id is stored in the SLF4J
 * {@link MDC} for the duration of the request, exposed as a request attribute, and
 * echoed back on the response header so the caller can correlate the response with
 * server-side logs.
 * <p>
 * Ordered to run before Spring Security's filter chain ({@code @Order(Integer.MIN_VALUE)})
 * so every request gets a correlation id, including requests rejected with 401/403.
 */
// TODO: define a contract so the FE also sends this header along on its own requests
@Component
@Order(Integer.MIN_VALUE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        // No id supplied by the caller (or upstream service) - generate one so this
        // request is still traceable end to end.
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        request.setAttribute(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear the MDC entry once the request completes, otherwise it can
            // leak into the next request handled by the same pooled thread.
            MDC.remove(MDC_KEY);
        }
    }
}
