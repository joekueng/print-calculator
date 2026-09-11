package com.printcalculator.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window rate limiter used to protect CPU- and I/O-intensive public
 * endpoints (slicing, quote estimation) from abuse. State is in-memory and
 * keyed by client IP, following the same client-key resolution strategy used
 * by the admin login throttle.
 */
@Service
public class QuoteRateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(QuoteRateLimitService.class);

    private final ConcurrentHashMap<String, ClientRequestState> requestsByClient = new ConcurrentHashMap<>();
    private final AtomicInteger evictionCounter = new AtomicInteger();

    private final int maxRequests;
    private final long windowMillis;
    private final boolean trustProxyHeaders;

    public QuoteRateLimitService(
            @Value("${quote.rate-limit.max-requests:15}") int maxRequests,
            @Value("${quote.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${quote.rate-limit.trust-proxy-headers:false}") boolean trustProxyHeaders
    ) {
        this.maxRequests = maxRequests > 0 ? maxRequests : 15;
        this.windowMillis = windowSeconds > 0 ? windowSeconds * 1000L : 60_000L;
        this.trustProxyHeaders = trustProxyHeaders;
    }

    /**
     * Records one request for the caller and rejects the request with HTTP 429
     * when the sliding-window budget for the client is exhausted.
     */
    public void checkAllowed(HttpServletRequest request) {
        String clientKey = resolveClientKey(request);
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;

        ClientRequestState state = requestsByClient.compute(clientKey, (key, current) -> {
            if (current == null || current.lastRequestAt <= windowStart) {
                return new ClientRequestState(now);
            }
            current.requests++;
            current.lastRequestAt = now;
            return current;
        });

        if (state.requests > maxRequests) {
            logger.warn("Rate limit exceeded for quote endpoint by client {}", clientKey);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Please wait a moment and try again."
            );
        }

        if ((evictionCounter.incrementAndGet() & 0xFF) == 0) {
            evictStaleEntries(now);
        }
    }

    private void evictStaleEntries(long now) {
        long cutoff = now - windowMillis;
        requestsByClient.entrySet().removeIf(entry -> entry.getValue().lastRequestAt <= cutoff);
    }

    public String resolveClientKey(HttpServletRequest request) {
        if (trustProxyHeaders && request != null) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] parts = forwardedFor.split(",");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    return parts[0].trim();
                }
            }

            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }

        if (request == null) {
            return "unknown";
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress != null && !remoteAddress.isBlank() ? remoteAddress.trim() : "unknown";
    }

    private static final class ClientRequestState {
        private int requests;
        private long lastRequestAt;

        private ClientRequestState(long lastRequestAt) {
            this.requests = 1;
            this.lastRequestAt = lastRequestAt;
        }
    }
}
