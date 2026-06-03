package org.example.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Logs each HTTP request as "METHOD /path → STATUS (Nms)" using the "http" logger. */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http");

    /** Passes the request through and logs the result on completion. */
    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            String uri = req.getRequestURI()
                    + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
            log.info("{} {} → {} ({}ms)",
                    req.getMethod(),
                    uri,
                    res.getStatus(),
                    System.currentTimeMillis() - start);
        }
    }
}
