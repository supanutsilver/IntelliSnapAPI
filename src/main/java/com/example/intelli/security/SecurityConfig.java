package com.example.intelli.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    private final JwtTokenUtil jwtTokenUtil;

    @Autowired
    public SecurityConfig(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Bean
    public UserDetailsService userDetailsServiceBean() { 
        return username -> {
            logger.info("UserDetailsService (SecurityConfig): Creating UserDetails for deviceId (username): {}", username);
            return new User(username, "", new ArrayList<>()); 
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        // Spring injects the 'userDetailsServiceBean' here as 'userDetailsService'
        JwtAuthenticationFilter customJwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenUtil, userDetailsService);

        http
            .csrf(csrf -> csrf.disable()) 
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) 
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/auth/**").permitAll() 
                .requestMatchers("/intelli/**").authenticated() 
                .anyRequest().authenticated() 
            )
            .addFilterBefore(customJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); 

        return http.build();
    }
}
