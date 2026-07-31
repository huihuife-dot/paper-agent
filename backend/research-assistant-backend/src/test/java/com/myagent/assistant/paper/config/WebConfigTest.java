package com.myagent.assistant.paper.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    void corsConfigAllowsPatchRequests() throws Exception {
        CorsRegistry registry = new CorsRegistry();

        new WebConfig().addCorsMappings(registry);

        CorsConfiguration apiCorsConfig = getCorsConfigurations(registry).get("/api/**");
        assertThat(apiCorsConfig).isNotNull();
        assertThat(apiCorsConfig.getAllowedMethods()).contains("PATCH");
    }

    @SuppressWarnings("unchecked")
    private Map<String, CorsConfiguration> getCorsConfigurations(CorsRegistry registry) throws Exception {
        Method method = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        method.setAccessible(true);
        return (Map<String, CorsConfiguration>) method.invoke(registry);
    }
}
