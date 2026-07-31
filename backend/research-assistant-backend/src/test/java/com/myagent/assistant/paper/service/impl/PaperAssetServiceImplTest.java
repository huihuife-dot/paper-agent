package com.myagent.assistant.paper.service.impl;

import com.myagent.assistant.paper.entity.PaperAsset;
import com.myagent.assistant.paper.mapper.PaperAssetMapper;
import com.myagent.assistant.paper.mapper.PaperMultimodalJobMapper;
import com.myagent.assistant.paper.mapper.PaperReferenceMapper;
import com.myagent.assistant.vision.service.VisionModelService;
import com.myagent.assistant.paper.service.MultimodalJobAsyncExecutor;
import com.myagent.assistant.paper.service.PaperReproductionFactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperAssetServiceImplTest {
    @TempDir Path tempDir;

    @Test
    void captionDetectionHandlesRunningHeaderAndBareTableLine() {
        String text = """
                Applied Energy 333 (2023) 120565L. Bentsen et al.Fig. 3. Visualisation of the GNN architecture.
                Figure 7: Average error at each station.
                The model is illustrated in Fig. 3, and evaluated below.
                Table 1
                Parameters and model structures for different models.
                """;

        List<PaperAssetServiceImpl.CaptionCandidate> candidates =
                PaperAssetServiceImpl.captionCandidates(text);

        assertThat(candidates).extracting(
                        PaperAssetServiceImpl.CaptionCandidate::type,
                        PaperAssetServiceImpl.CaptionCandidate::label)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("FIGURE", "Fig. 3"),
                        org.assertj.core.groups.Tuple.tuple("FIGURE", "Figure 7"),
                        org.assertj.core.groups.Tuple.tuple("TABLE", "Table 1"));
    }

    @Test
    void providerFailureKeepsOriginalAssetUnchanged() throws Exception {
        PaperAssetMapper assetMapper = mock(PaperAssetMapper.class);
        VisionModelService vision = mock(VisionModelService.class);
        PaperAsset asset = figureAsset();
        when(assetMapper.selectOne(any())).thenReturn(asset);
        when(vision.analysisVersion()).thenReturn("qwen-vl-max:vision-schema-v1");
        when(vision.analyze(any(), any(), any(), any())).thenThrow(new RuntimeException("provider unavailable"));
        Path image = tempDir.resolve("paper-assets/38/rev/pages/page-0001.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[]{1, 2, 3});
        PaperAssetServiceImpl service = service(assetMapper, vision);

        assertThatThrownBy(() -> service.analyze(38L, 9L)).hasMessage("provider unavailable");
        verify(assetMapper, never()).updateById(any(PaperAsset.class));
    }

    @Test
    void sameAnalysisVersionReturnsCachedResultWithoutProviderCall() {
        PaperAssetMapper assetMapper = mock(PaperAssetMapper.class);
        VisionModelService vision = mock(VisionModelService.class);
        PaperAsset asset = figureAsset();
        asset.setAnalysisVersion("qwen-vl-max:vision-schema-v1");
        asset.setSemanticDescription("{\"purpose\":\"cached\"}");
        when(assetMapper.selectOne(any())).thenReturn(asset);
        when(vision.analysisVersion()).thenReturn(asset.getAnalysisVersion());
        PaperAssetServiceImpl service = service(assetMapper, vision);

        service.analyze(38L, 9L);

        verify(vision, never()).analyze(any(), any(), any(), any());
        verify(assetMapper, never()).updateById(any(PaperAsset.class));
    }

    @Test
    void humanReviewInvalidatesDerivedFactsAndVectors() {
        PaperAssetMapper assetMapper = mock(PaperAssetMapper.class);
        VisionModelService vision = mock(VisionModelService.class);
        PaperReproductionFactService facts = mock(PaperReproductionFactService.class);
        PaperAsset asset = figureAsset();
        when(assetMapper.selectOne(any())).thenReturn(asset);
        PaperAssetServiceImpl service = service(assetMapper, vision, facts);

        service.confirm(38L, 9L, true);

        verify(assetMapper).updateById(asset);
        verify(facts).invalidateForPaper(38L);
    }

    private PaperAssetServiceImpl service(PaperAssetMapper mapper, VisionModelService vision) {
        return service(mapper, vision, mock(PaperReproductionFactService.class));
    }

    private PaperAssetServiceImpl service(PaperAssetMapper mapper, VisionModelService vision,
                                          PaperReproductionFactService facts) {
        PaperAssetServiceImpl service = new PaperAssetServiceImpl(
                mock(PaperReferenceMapper.class), mapper, vision, mock(PaperMultimodalJobMapper.class),
                mock(MultimodalJobAsyncExecutor.class), facts);
        ReflectionTestUtils.setField(service, "dataDir", tempDir.toString());
        return service;
    }

    private PaperAsset figureAsset() {
        PaperAsset asset = new PaperAsset();
        asset.setId(9L);
        asset.setPaperId(38L);
        asset.setAssetType("FIGURE");
        asset.setCaption("Model architecture");
        asset.setVerificationStatus("EXTRACTED");
        asset.setRawAssetPath("38/rev/pages/page-0001.png");
        return asset;
    }
}
