package com.myagent.assistant.researchengineering.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.paper.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** System-side index and compact activity timeline for local Agent projects. */
@RestController
@RequestMapping("/api/agent-projects")
public class AgentProjectController {
    private static final int MAX_ACTIVITY_ITEMS = 50;
    private final ObjectMapper mapper;
    private final Path file;

    public AgentProjectController(ObjectMapper mapper,
                                  @Value("${app.agent-projects-file:ResearchAssistantData/agent-projects.json}") String location) {
        this.mapper = mapper;
        this.file = Path.of(location);
    }

    @GetMapping
    public synchronized Result<List<Map<String, Object>>> list() {
        return Result.success(read());
    }

    @PostMapping
    public synchronized Result<Map<String, Object>> upsert(@RequestBody Map<String, Object> incoming) {
        String projectId = requiredText(incoming, "projectId");
        String mode = requiredText(incoming, "mode");
        String sourceId = sourceId(incoming);
        List<Map<String, Object>> records = read();
        Map<String, Object> previous = null;
        for (Map<String, Object> item : records) {
            if (projectId.equals(String.valueOf(item.get("projectId")))
                    || (sourceId != null && sourceId.equals(sourceId(item)) && mode.equals(String.valueOf(item.get("mode"))))) {
                previous = item;
                break;
            }
        }
        if (previous != null) records.remove(previous);

        Map<String, Object> merged = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
        List<Map<String, Object>> activityLog = activities(previous);
        merged.putAll(incoming);
        if (sourceId != null) merged.put("sourceId", sourceId);
        merged.put("updatedAt", LocalDateTime.now().toString());

        Map<String, Object> activity = activity(incoming, merged.get("updatedAt"));
        if (!isDuplicate(activityLog, activity)) activityLog.add(0, activity);
        while (activityLog.size() > MAX_ACTIVITY_ITEMS) activityLog.remove(activityLog.size() - 1);
        merged.put("activityLog", activityLog);
        merged.put("sessionCount", activityLog.stream().map(item -> item.get("sessionId"))
                .filter(Objects::nonNull).map(String::valueOf).distinct().count());
        records.add(0, merged);
        write(records);
        return Result.success(merged);
    }

    private String requiredText(Map<String, Object> record, String name) {
        String value = String.valueOf(record.getOrDefault(name, "")).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private String sourceId(Map<String, Object> record) {
        Object value = record.get("sourceId");
        if (value == null) value = record.get("paperId"); // read legacy paper records
        return value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value)) ? null : String.valueOf(value);
    }

    private List<Map<String, Object>> activities(Map<String, Object> record) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (record == null || !(record.get("activityLog") instanceof List<?> raw)) return result;
        for (Object item : raw) if (item instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            result.add(copy);
        }
        return result;
    }

    private Map<String, Object> activity(Map<String, Object> incoming, Object recordedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("recordedAt", recordedAt);
        copy(incoming, item, "status", "sessionId", "version", "summaryPath", "summary", "sourceRevision",
                "artifactPaths", "evidenceReadiness", "evidenceStrategy", "evidenceTracePath",
                "evidenceDecisionCounts");
        return item;
    }

    private void copy(Map<String, Object> from, Map<String, Object> to, String... names) {
        for (String name : names) if (from.containsKey(name) && from.get(name) != null) to.put(name, from.get(name));
    }

    private boolean isDuplicate(List<Map<String, Object>> activities, Map<String, Object> activity) {
        String key = String.valueOf(activity.get("status")) + "|" + String.valueOf(activity.get("sessionId"))
                + "|" + String.valueOf(activity.get("summaryPath"));
        return activities.stream().anyMatch(item -> key.equals(String.valueOf(item.get("status")) + "|"
                + String.valueOf(item.get("sessionId")) + "|" + String.valueOf(item.get("summaryPath"))));
    }

    private List<Map<String, Object>> read() {
        try {
            return Files.exists(file) ? mapper.readValue(Files.readString(file), new TypeReference<>() {}) : new ArrayList<>();
        } catch (Exception e) {
            throw new RuntimeException("could not read Agent project records", e);
        }
    }

    private void write(List<Map<String, Object>> records) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(records));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("could not save Agent project records", e);
        }
    }
}
