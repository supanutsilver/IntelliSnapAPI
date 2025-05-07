package com.example.intelli.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger customLogger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;

    @Autowired
    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil, UserDetailsService userDetailsService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String requestTokenHeader = request.getHeader("Authorization");

        // Log the Authorization header for debugging purposes, especially for relevant paths
        if (request.getRequestURI().startsWith("/intelli/")) {
            customLogger.info("Request URI: {}", request.getRequestURI());
            customLogger.info("Authorization Header: {}", requestTokenHeader);
        }

        String username = null;
        String jwtToken = null;

        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            } catch (IllegalArgumentException e) {
                customLogger.warn("JWT Token is invalid: Unable to get username from token. {}", e.getMessage());
            } catch (ExpiredJwtException e) {
                customLogger.warn("JWT Token has expired: {}", e.getMessage());
            } catch (MalformedJwtException e) {
                customLogger.warn("JWT Token is malformed: {}", e.getMessage());
            } catch (SignatureException e) {
                customLogger.warn("JWT Token signature validation failed: {}", e.getMessage());
            } catch (UnsupportedJwtException e) {
                customLogger.warn("JWT Token is unsupported: {}", e.getMessage());
            } catch (Exception e) {
                customLogger.error("An unexpected error occurred while parsing JWT Token: {}", e.getMessage(), e);
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                customLogger.debug("User '{}' (deviceId) authenticated successfully with JWT for request: {}", username, request.getRequestURI());
            } else {
                customLogger.warn("JWT Token validation failed for user '{}' (deviceId) for request: {}. Token might be invalid or expired, or user details mismatch.", username, request.getRequestURI());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
