package com.flashdrop.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashdrop.common.ClaimResult;
import com.flashdrop.common.ClaimStatus;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ProxyManager<String> proxyManager;
    private final ObjectMapper objectMapper;
    private final int capacity;
    private final int refillPeriodSeconds;

    public RateLimitInterceptor(
            @Lazy ProxyManager<String> proxyManager,
            ObjectMapper objectMapper,
            @Value("${flashdrop.rate-limit.capacity:5}") int capacity,
            @Value("${flashdrop.rate-limit.refill-period-seconds:10}") int refillPeriodSeconds
    ) {
        this.proxyManager = proxyManager;
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.refillPeriodSeconds = refillPeriodSeconds;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = resolveKey(request);

        Supplier<BucketConfiguration> configuration = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofSeconds(refillPeriodSeconds))
                        .build())
                .build();

        ConsumptionProbe probe = proxyManager.getProxy(key, configuration).tryConsumeAndReturnRemaining(1);

        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            return true;
        }

        long waitSeconds = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("Retry-After", String.valueOf(waitSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(new ClaimResult(ClaimStatus.RATE_LIMITED, null)));

        return false;
    }

    private String resolveKey(HttpServletRequest request) {
        String userId = request.getParameter("userId");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return request.getRemoteAddr();
    }
}
