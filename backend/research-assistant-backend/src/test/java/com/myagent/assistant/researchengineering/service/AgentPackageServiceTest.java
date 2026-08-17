package com.myagent.assistant.researchengineering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.researchengineering.config.AgentDeliveryProperties;
import com.myagent.assistant.researchengineering.dto.AgentEvidenceResponse;
import com.myagent.assistant.researchengineering.dto.AgentPackageResponse;
import com.myagent.assistant.researchengineering.dto.AgentTaskPackageResponse;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPackageServiceTest {
    @TempDir Path tempDir;

    @Test
    void exportsVersionedPaperPackageWithTaskEvidenceAndManifest() throws Exception {
        ResearchEngineeringContextService contexts = mock(ResearchEngineeringContextService.class);
        AgentEvidenceResponse evidence = new AgentEvidenceResponse("fact-1", "paper_reproduction_fact", "LSTM 层数", "两层 LSTM", "paper:38:p12");
        AgentTaskPackageResponse task = new AgentTaskPackageResponse("PAPER_REPRODUCTION", "最小复现",
                List.of("实现模型"), List.of(), List.of(), List.of(evidence), List.of(),
                new AgentTaskPackageResponse.CodeScope(List.of("src/**"), List.of(".env"), "受限范围"), List.of("运行短测试"));
        PaperReproductionContextResponse context = new PaperReproductionContextResponse("paper-38-rev-1",
                new PaperReproductionContextResponse.AgentPaperResponse(38L, "Demo Paper", "abstract", "paper:38", List.of(), "now"),
                List.of(evidence), task);
        when(contexts.getPaperReproductionContext(38L, 2)).thenReturn(context);
        AgentDeliveryProperties properties = new AgentDeliveryProperties();
        properties.setPackageRoot(tempDir.resolve("packages").toString());
        AgentPackageService service = new AgentPackageService(contexts, new ObjectMapper(), properties);

        AgentPackageResponse result = service.createPaperPackage(38L);
        List<String> entries = new ArrayList<>();
        try (InputStream input = service.packageResource(result.packageId()).getInputStream();
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }

        assertTrue(entries.contains("manifest.json"));
        assertTrue(entries.contains("task/task-package.json"));
        assertTrue(entries.contains("task/reproduction-spec.json"));
        assertTrue(entries.contains("evidence/evidence.json"));
        assertTrue(entries.contains("handoff/handoff-template.json"));
    }
}
