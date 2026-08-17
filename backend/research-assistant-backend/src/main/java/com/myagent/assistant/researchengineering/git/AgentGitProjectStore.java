package com.myagent.assistant.researchengineering.git;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import org.springframework.stereotype.Component;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class AgentGitProjectStore {
    private final ObjectMapper mapper;
    private final Path file;

    public AgentGitProjectStore(ObjectMapper mapper, AgentDeliveryProperties properties) {
        this.mapper = mapper;
        this.file = Path.of(properties.getProjectIndexFile()).toAbsolutePath().normalize();
    }

    public synchronized List<AgentGitProjectDocument> list() {
        try {
            return Files.isRegularFile(file)
                    ? mapper.readValue(Files.readString(file), new TypeReference<>() {})
                    : new ArrayList<>();
        } catch (Exception e) {
            throw new IllegalStateException("无法读取 Agent Git 项目索引", e);
        }
    }

    public synchronized AgentGitProjectDocument find(String mode, long sourceId) {
        return list().stream().filter(item -> mode.equals(item.getMode()) && Long.valueOf(sourceId).equals(item.getSourceId()))
                .findFirst().orElse(null);
    }

    public synchronized AgentGitProjectDocument save(AgentGitProjectDocument project) {
        List<AgentGitProjectDocument> projects = list();
        projects.removeIf(item -> project.getProjectId().equals(item.getProjectId()));
        projects.add(0, project);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(projects));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return project;
        } catch (Exception e) {
            throw new IllegalStateException("无法保存 Agent Git 项目索引", e);
        }
    }
}
