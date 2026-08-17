package com.myagent.assistant.researchengineering.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class HttpGiteeRepositoryClient implements GiteeRepositoryClient {
    private final AgentDeliveryProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public HttpGiteeRepositoryClient(AgentDeliveryProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public GiteeRepository createPrivateRepository(String name, String description) {
        requireConfigured();
        String form = "access_token=" + encode(properties.getGiteeToken())
                + "&name=" + encode(name) + "&private=true&auto_init=false"
                + "&description=" + encode(description == null ? "MyAgent 论文复现项目" : description);
        HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(properties.getGiteeBaseUrl()) + "/user/repos"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gitee 创建仓库失败，HTTP " + response.statusCode());
            }
            JsonNode body = mapper.readTree(response.body());
            String fullName = text(body, "full_name", properties.getGiteeOwner() + "/" + name);
            String htmlUrl = text(body, "html_url", "https://gitee.com/" + fullName);
            String sshUrl = text(body, "ssh_url", "git@gitee.com:" + fullName + ".git");
            return new GiteeRepository(fullName, htmlUrl, sshUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gitee 请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("无法调用 Gitee API", e);
        }
    }

    private void requireConfigured() {
        if (!properties.isGiteeEnabled()) throw new IllegalStateException("Gitee 同步未启用");
        if (properties.getGiteeToken() == null || properties.getGiteeToken().isBlank()) throw new IllegalStateException("未配置 GITEE_ACCESS_TOKEN");
        if (properties.getGiteeOwner() == null || properties.getGiteeOwner().isBlank()) throw new IllegalStateException("未配置 GITEE_OWNER");
    }

    private String text(JsonNode node, String name, String fallback) {
        return node.hasNonNull(name) && !node.get(name).asText().isBlank() ? node.get(name).asText() : fallback;
    }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private String trimSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
}
