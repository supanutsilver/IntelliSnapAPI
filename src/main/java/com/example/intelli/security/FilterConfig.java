package com.example.intelli.security;

import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
    /* // Commenting out as JwtAuthenticationFilter is now registered via SecurityConfig
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("/intelli/*"); // Protect all /intelli endpoints
        return registrationBean;
    }
    */
}
