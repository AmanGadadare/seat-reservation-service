package com.example.seat_reservation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class UserIdentityFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "USER_ID";
    public static final String ROLE_ATTRIBUTE = "ROLE";

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader("X-Request-ID");

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);

        response.setHeader("X-Request-ID", requestId);

        try {
            String authorization = request.getHeader("Authorization");

            if (authorization != null
                    && authorization.startsWith("Bearer ")) {

                String token = authorization.substring(7).trim();

                if (!token.isBlank()) {

                    if ("admin".equals(token)) {

                        request.setAttribute(
                                USER_ID_ATTRIBUTE,
                                "admin"
                        );

                        request.setAttribute(
                                ROLE_ATTRIBUTE,
                                ADMIN_ROLE
                        );

                    } else {

                        request.setAttribute(
                                USER_ID_ATTRIBUTE,
                                token
                        );

                        request.setAttribute(
                                ROLE_ATTRIBUTE,
                                USER_ROLE
                        );
                    }
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            MDC.remove("requestId");
        }
    }
}