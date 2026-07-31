package com.myagent.assistant.researchengineering.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.myagent.assistant.idea.entity.ResearchIdea;
import com.myagent.assistant.idea.service.ResearchIdeaService;
import com.myagent.assistant.paper.entity.PaperProfile;
import com.myagent.assistant.paper.entity.PaperReference;
import com.myagent.assistant.paper.entity.PaperSectionSummary;
import com.myagent.assistant.paper.entity.PaperChunk;
import com.myagent.assistant.paper.mapper.PaperProfileMapper;
import com.myagent.assistant.paper.mapper.PaperSectionSummaryMapper;
import com.myagent.assistant.paper.mapper.PaperChunkMapper;
import com.myagent.assistant.paper.service.PaperReferenceService;
import com.myagent.assistant.paper.service.PaperReproductionSpecService;
import com.myagent.assistant.paper.reproduction.ReproductionSpecDocument;
import com.myagent.assistant.researchengineering.dto.AgentEvidenceResponse;
import com.myagent.assistant.researchengineering.dto.IdeaImprovementContextResponse;
import com.myagent.assistant.researchengineering.dto.PaperReproductionContextResponse;
import com.myagent.assistant.researchengineering.dto.AgentTaskPackageResponse;
import com.myagent.assistant.researchengineering.service.ResearchEngineeringContextService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * Keeps controller code thin and makes the online-to-local data boundary explicit.
 * This service is read-only: it does not create projects, modify Ideas, or run code.
 */
@Service
public class ResearchEngineeringContextServiceImpl implements ResearchEngineeringContextService {
    private static final String PROFILE_VERSION = "paper-profile-v1";
    private static final String SUMMARY_VERSION = "section-summary-v1";
    /** Keep remote context useful without consuming an entire model window. */
    private static final int MAX_SECTION_EVIDENCE_COUNT = 16;
    private static final int MAX_PAPER_EVIDENCE_CHARS = 3_000;
    private static final int MAX_PROFILE_EVIDENCE_CHARS = 8_000;
    private static final int MAX_SECTION_EVIDENCE_CHARS = 1_800;
    private static final int MAX_IDEA_RELATED_FACTS_PER_PAPER = 8;
    private static final int MAX_IDEA_RELATED_CHUNKS_PER_PAPER = 2;

    private final ResearchIdeaService researchIdeaService;
    private final PaperReferenceService paperReferenceService;
    private final PaperProfileMapper paperProfileMapper;
    private final PaperSectionSummaryMapper sectionSummaryMapper;
    private final PaperChunkMapper paperChunkMapper;
    private final PaperReproductionSpecService reproductionSpecService;

    public ResearchEngineeringContextServiceImpl(ResearchIdeaService researchIdeaService,
                                                 PaperReferenceService paperReferenceService,
                                                 PaperProfileMapper paperProfileMapper,
                                                 PaperSectionSummaryMapper sectionSummaryMapper,
                                                 PaperChunkMapper paperChunkMapper,
                                                 PaperReproductionSpecService reproductionSpecService) {
        this.researchIdeaService = researchIdeaService;
        this.paperReferenceService = paperReferenceService;
        this.paperProfileMapper = paperProfileMapper;
        this.sectionSummaryMapper = sectionSummaryMapper;
        this.paperChunkMapper = paperChunkMapper;
        this.reproductionSpecService = reproductionSpecService;
    }

    @Override
    public IdeaImprovementContextResponse getIdeaImprovementContext(Long ideaId) {
        ResearchIdea idea = researchIdeaService.getById(ideaId);
        List<AgentEvidenceResponse> evidence = new ArrayList<>();
        evidence.add(new AgentEvidenceResponse(
                "idea:" + idea.getId(), "idea", safeTitle(idea.getTitle(), "Research Idea"),
                joinNonBlank(idea.getRefinedContent(), idea.getOriginalContent(), idea.getInnovationPoints()),
                "idea:" + idea.getId()));
        for (Long paperId : parseIds(idea.getRelatedPaperIds())) {
            try {
                PaperReference paper = paperReferenceService.getPaperById(paperId);
                evidence.add(paperEvidence(paper));
                ReproductionSpecDocument spec = reproductionSpecService.get(paperId);
                if (spec != null) evidence.addAll(ideaRelatedFactEvidence(idea, spec));
                evidence.addAll(ideaRelatedRawChunkEvidence(idea, paperId));
            } catch (RuntimeException ignored) {
                // An old Idea may reference a paper that has since been deleted.
                // Keep the Idea usable instead of failing the whole context export.
            }
        }
        IdeaImprovementContextResponse.AgentIdeaResponse payload = new IdeaImprovementContextResponse.AgentIdeaResponse(
                idea.getId(), safeTitle(idea.getTitle(), "Untitled Idea"), valueOrEmpty(idea.getOriginalContent()),
                blankToNull(idea.getRefinedContent()), blankToNull(idea.getResearchQuestion()),
                blankToNull(idea.getPossibleMethod()), blankToNull(idea.getInnovationPoints()), parseTags(idea.getTags()),
                idea.getSourceSessionId(), idea.getSourceMessageId(), timestamp(idea.getUpdateTime(), idea.getCreateTime()));
        return new IdeaImprovementContextResponse(timestamp(idea.getUpdateTime(), idea.getCreateTime()), payload, evidence,
                ideaTaskPackage(payload, evidence));
    }

    @Override
    public PaperReproductionContextResponse getPaperReproductionContext(Long paperId) {
        return getPaperReproductionContext(paperId, 2);
    }

    @Override
    public PaperReproductionContextResponse getPaperReproductionContext(Long paperId, int protocolVersion) {
        PaperReference paper = paperReferenceService.getPaperById(paperId);
        List<AgentEvidenceResponse> evidence = new ArrayList<>();
        evidence.add(paperEvidence(paper));
        PaperProfile profile = paperProfileMapper.selectOne(new QueryWrapper<PaperProfile>()
                .eq("paper_id", paperId).eq("profile_version", PROFILE_VERSION));
        if (profile != null && !joinNonBlank(profile.getResearchProblem(), profile.getMethodSummary(), profile.getProfileText()).isBlank()) {
            evidence.add(new AgentEvidenceResponse("paper-profile:" + paperId, "paper_profile", safeTitle(paper.getTitle(), "Paper") + " profile",
                    truncate(joinNonBlank(profile.getResearchProblem(), profile.getMethodSummary(), profile.getKeyContributions(), profile.getLimitations(), profile.getProfileText()), MAX_PROFILE_EVIDENCE_CHARS),
                    "paper-profile:" + paperId));
        }
        List<PaperSectionSummary> summaries = sectionSummaryMapper.selectList(new QueryWrapper<PaperSectionSummary>()
                .eq("paper_id", paperId).eq("summary_version", SUMMARY_VERSION).orderByAsc("section_id"));
        for (PaperSectionSummary summary : summaries.stream()
                .sorted(Comparator.comparingInt(this::summaryPriority).reversed())
                .limit(MAX_SECTION_EVIDENCE_COUNT)
                .toList()) {
            String content = joinNonBlank(summary.getSummary(), summary.getKeyPoints());
            if (!content.isBlank()) {
                evidence.add(new AgentEvidenceResponse("paper-section:" + summary.getId(), "paper_section_summary",
                        safeTitle(summary.getSectionTitle(), "Paper section"), truncate(content, MAX_SECTION_EVIDENCE_CHARS), "paper-section:" + summary.getId()));
            }
        }
        ReproductionSpecDocument spec = protocolVersion >= 2 ? reproductionSpecService.get(paperId) : null;
        if (protocolVersion >= 2) {
            evidence.addAll(rawChunkEvidence(paperId));
            if (spec != null) evidence.addAll(reproductionFactEvidence(spec));
        }
        String abstractText = firstNonBlank(paper.getAbstractText(), profile == null ? null : profile.getProfileText(), paper.getRemark(), "No abstract is available in MyAgent.");
        PaperReproductionContextResponse.AgentPaperResponse payload = new PaperReproductionContextResponse.AgentPaperResponse(
                paper.getId(), safeTitle(paper.getTitle(), "Untitled Paper"), abstractText,
                "/api/papers/" + paper.getId() + "/content", Collections.emptyList(), timestamp(paper.getUploadTime()));
        String revision = spec == null ? timestamp(paper.getUploadTime()) : spec.getSourceRevision();
        return new PaperReproductionContextResponse(revision, payload, evidence,
                paperTaskPackage(payload, evidence, spec, protocolVersion),
                protocolVersion >= 2 ? "agent-context-v2" : "agent-context-v1", spec);
    }

    /** Turn database fields into a small contract instead of asking the Agent to infer a task from a raw dump. */
    private AgentTaskPackageResponse paperTaskPackage(PaperReproductionContextResponse.AgentPaperResponse paper,
                                                      List<AgentEvidenceResponse> evidence,
                                                      ReproductionSpecDocument spec,
                                                      int protocolVersion) {
        if (protocolVersion < 2) return paperTaskPackageV1(paper, evidence);
        List<String> gaps = new ArrayList<>(List.of(
                "Paper context may still omit author code, datasets, checkpoints, and complete experiment settings.",
                "Any missing implementation detail must be labelled as an assumption, not silently invented."));
        if (spec == null) gaps.add("ReproductionSpec is not built; use only the supplied v1 evidence.");
        else gaps.addAll(spec.getMissingInformation());
        List<AgentTaskPackageResponse.ParameterItem> parameters = new ArrayList<>();
        parameters.add(new AgentTaskPackageResponse.ParameterItem("paperId", String.valueOf(paper.getPaperId()), "MyAgent selected paper"));
        if (spec != null) {
            spec.getTrustedFacts().stream()
                    .filter(fact -> fact.getFactType().contains("HYPERPARAMETER"))
                    .limit(20)
                    .forEach(fact -> parameters.add(new AgentTaskPackageResponse.ParameterItem(
                            fact.getKey(), fact.getValue() + (fact.getUnit() == null ? "" : " " + fact.getUnit()),
                            "fact:" + fact.getFactId() + "; page:" + fact.getPageNumber())));
        }
        return new AgentTaskPackageResponse(
                "PAPER_REPRODUCTION",
                "Reproduce a runnable, minimal implementation for: " + paper.getTitle(),
                List.of(
                        "Read ReproductionSpec first; use trustedFacts as paper facts and keep review/model-inferred facts non-authoritative.",
                        "Read the supplied raw-chunk and asset evidence for the exact source behind each implementation decision.",
                        "Surface every conflict and missing critical item before choosing an implementation assumption.",
                        "Create a small, understandable project structure and implement the supported core method.",
                        "Expose one runnable entry point with safe local defaults.",
                        "Run one short syntax, import, unit, or smoke check when the local user permits it.",
                        "In the handoff, separate evidence-backed facts, model inferences, safe defaults, conflicts, and missing information."),
                List.of(
                        new AgentTaskPackageResponse.InterfaceItem("input", "Use a small local or synthetic input when the paper dataset is not supplied."),
                        new AgentTaskPackageResponse.InterfaceItem("output", "Produce source code plus a runnable entry point; do not claim research results.")),
                parameters,
                evidence,
                gaps,
                new AgentTaskPackageResponse.CodeScope(Collections.emptyList(), Collections.emptyList(),
                        "The local Agent adds its isolated workspace scope after the user starts reproduction."),
                List.of("Source files are created.", "A documented runnable entry point exists.",
                        "One short basic check is recorded when it was actually run.",
                        "Handoff lists evidence-backed facts, assumptions, conflicts, and missing information."),
                "agent-task-package-v2", spec,
                spec == null ? List.of() : spec.getConflicts(),
                spec == null ? List.of("ReproductionSpec is not built") : spec.getMissingInformation(),
                spec == null ? List.of() : spec.getSafeDefaults());
    }

    private AgentTaskPackageResponse paperTaskPackageV1(PaperReproductionContextResponse.AgentPaperResponse paper,
                                                        List<AgentEvidenceResponse> evidence) {
        return new AgentTaskPackageResponse(
                "PAPER_REPRODUCTION",
                "Reproduce a runnable, minimal implementation for: " + paper.getTitle(),
                List.of("Read the supplied evidence and identify only the method details it explicitly supports.",
                        "Create a small, understandable project structure and implement the supported core method.",
                        "Expose one runnable entry point with safe local defaults.",
                        "Run one short syntax, import, unit, or smoke check when the local user permits it."),
                List.of(new AgentTaskPackageResponse.InterfaceItem("input", "Use a small local or synthetic input when the paper dataset is not supplied."),
                        new AgentTaskPackageResponse.InterfaceItem("output", "Produce source code plus a runnable entry point; do not claim research results.")),
                List.of(new AgentTaskPackageResponse.ParameterItem("paperId", String.valueOf(paper.getPaperId()), "MyAgent selected paper")),
                evidence,
                List.of("Paper context may omit equations, hyperparameters, datasets, and author code.",
                        "Any missing implementation detail must be labelled as an assumption, not silently invented."),
                new AgentTaskPackageResponse.CodeScope(Collections.emptyList(), Collections.emptyList(),
                        "The local Agent adds its isolated workspace scope after the user starts reproduction."),
                List.of("Source files are created.", "A documented runnable entry point exists.",
                        "One short basic check is recorded when it was actually run."));
    }

    private AgentTaskPackageResponse ideaTaskPackage(IdeaImprovementContextResponse.AgentIdeaResponse idea,
                                                     List<AgentEvidenceResponse> evidence) {
        String method = firstNonBlank(idea.getPossibleMethod(), "No concrete method was supplied; inspect the existing code before choosing a minimal change.");
        return new AgentTaskPackageResponse(
                "IDEA_CODE_IMPROVEMENT",
                "Implement one focused improvement in the selected local codebase for Idea: " + idea.getTitle(),
                List.of(
                        "Inspect the permitted local code before editing and preserve unrelated behavior.",
                        "Treat the Idea as the target, the local RepositoryBaseline as current code truth, and related-paper facts only as method support.",
                        "Map the Idea's method to the smallest compatible code change: " + method,
                        "Add or update a clear API, configuration, or module only where needed.",
                        "Run one short existing check or syntax/import check when the local user permits it."),
                List.of(
                        new AgentTaskPackageResponse.InterfaceItem("existing-code", "The selected local repository is the source of truth for current interfaces."),
                        new AgentTaskPackageResponse.InterfaceItem("output", "Deliver a focused code change and a concise handoff; do not run research experiments.")),
                List.of(new AgentTaskPackageResponse.ParameterItem("ideaId", String.valueOf(idea.getIdeaId()), "MyAgent selected Idea")),
                evidence,
                List.of(
                        "The online system does not know the local repository layout or its dependencies.",
                        "Related-paper abstracts provide context; only evidence with a trusted verification status may support exact method details.",
                        "The Agent must not modify paths outside the repository baseline selected by the user.",
                        "Missing algorithm details must be implemented as explicit safe defaults or reported as blocked."),
                new AgentTaskPackageResponse.CodeScope(Collections.emptyList(), Collections.emptyList(),
                        "The localhost Bridge fills allowed and prohibited paths from the selected repository baseline."),
                List.of("Only approved repository paths are changed.", "Existing interfaces are preserved unless the handoff states otherwise.", "One short basic check is recorded when it was actually run."));
    }

    private AgentEvidenceResponse paperEvidence(PaperReference paper) {
        return new AgentEvidenceResponse("paper:" + paper.getId(), "paper", safeTitle(paper.getTitle(), "Paper"),
                truncate(joinNonBlank(paper.getAbstractText(), paper.getKeywords(), paper.getRemark()), MAX_PAPER_EVIDENCE_CHARS), "paper:" + paper.getId());
    }

    private List<AgentEvidenceResponse> rawChunkEvidence(Long paperId) {
        List<PaperChunk> chunks = paperChunkMapper.selectList(new QueryWrapper<PaperChunk>()
                .eq("paper_id", paperId).eq("is_reference", false).eq("is_noise", false));
        return chunks.stream()
                .filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank())
                .sorted(Comparator.comparingInt(this::rawChunkPriority).reversed()
                        .thenComparing(chunk -> chunk.getChunkIndex() == null ? Integer.MAX_VALUE : chunk.getChunkIndex()))
                .limit(8)
                .map(chunk -> new AgentEvidenceResponse(
                        "paper-chunk:" + chunk.getId(), "paper_raw_chunk",
                        safeTitle(chunk.getSectionTitle(), "Paper source excerpt"),
                        truncate(chunk.getContent(), 1_400), "paper-chunk:" + chunk.getId(),
                        paperId, "RAW_CHUNK", chunk.getId(),
                        chunk.getPageNumber() != null ? chunk.getPageNumber() : chunk.getPageStart(),
                        "EXTRACTED", chunk.getQualityScore()))
                .toList();
    }

    private int rawChunkPriority(PaperChunk chunk) {
        String text = (valueOrEmpty(chunk.getSectionType()) + " "
                + valueOrEmpty(chunk.getSectionTitle()) + " "
                + valueOrEmpty(chunk.getContent())).toLowerCase();
        int score = 0;
        if (text.contains("method") || text.contains("algorithm") || text.contains("model")) score += 4;
        if (text.contains("learning rate") || text.contains("batch size") || text.contains("epoch")) score += 3;
        if (text.contains("loss") || text.contains("objective") || text.contains("dataset")) score += 2;
        if (text.contains("experiment") || text.contains("implementation")) score += 1;
        return score;
    }

    private List<AgentEvidenceResponse> reproductionFactEvidence(ReproductionSpecDocument spec) {
        return spec.getTrustedFacts().stream()
                .sorted(Comparator.comparingInt(this::factPriority).reversed()
                        .thenComparing(fact -> fact.getPageNumber() == null ? Integer.MAX_VALUE : fact.getPageNumber()))
                .limit(40)
                .map(fact -> new AgentEvidenceResponse(
                        "reproduction-fact:" + fact.getFactId(), "paper_reproduction_fact",
                        fact.getFactType() + " · " + fact.getKey(), fact.getValue(),
                        "reproduction-fact:" + fact.getFactId(),
                        spec.getPaperId(), fact.getSourceKind(), fact.getSourceId(), fact.getPageNumber(),
                        fact.getVerificationStatus(), fact.getConfidence()))
                .toList();
    }

    private List<AgentEvidenceResponse> ideaRelatedFactEvidence(ResearchIdea idea,
                                                                ReproductionSpecDocument spec) {
        Set<String> terms = ideaTerms(idea);
        return spec.getTrustedFacts().stream()
                .sorted(Comparator.comparingInt((ReproductionSpecDocument.FactItem fact) ->
                                ideaFactScore(fact, terms)).reversed()
                        .thenComparing(fact -> fact.getPageNumber() == null ? Integer.MAX_VALUE : fact.getPageNumber()))
                .limit(MAX_IDEA_RELATED_FACTS_PER_PAPER)
                .map(fact -> new AgentEvidenceResponse(
                        "idea-paper-fact:" + fact.getFactId(), "idea_related_paper_fact",
                        "Related paper · " + fact.getFactType() + " · " + fact.getKey(),
                        fact.getValue() + (fact.getUnit() == null || fact.getUnit().isBlank() ? "" : " " + fact.getUnit()),
                        "reproduction-fact:" + fact.getFactId(),
                        spec.getPaperId(), fact.getSourceKind(), fact.getSourceId(), fact.getPageNumber(),
                        fact.getVerificationStatus(), fact.getConfidence()))
                .toList();
    }

    private List<AgentEvidenceResponse> ideaRelatedRawChunkEvidence(ResearchIdea idea, Long paperId) {
        Set<String> terms = ideaTerms(idea);
        List<PaperChunk> chunks = paperChunkMapper.selectList(new QueryWrapper<PaperChunk>()
                .eq("paper_id", paperId).eq("is_reference", false).eq("is_noise", false));
        return chunks.stream()
                .filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank())
                .sorted(Comparator.comparingInt((PaperChunk chunk) ->
                                ideaTextScore(joinNonBlank(chunk.getSectionTitle(), chunk.getContent()), terms)
                                        + rawChunkPriority(chunk)).reversed()
                        .thenComparing(chunk -> chunk.getChunkIndex() == null ? Integer.MAX_VALUE : chunk.getChunkIndex()))
                .limit(MAX_IDEA_RELATED_CHUNKS_PER_PAPER)
                .map(chunk -> new AgentEvidenceResponse(
                        "idea-paper-chunk:" + chunk.getId(), "idea_related_paper_raw_chunk",
                        "Related paper source · " + safeTitle(chunk.getSectionTitle(), "Paper source excerpt"),
                        truncate(chunk.getContent(), 1_200), "paper-chunk:" + chunk.getId(),
                        paperId, "RAW_CHUNK", chunk.getId(),
                        chunk.getPageNumber() != null ? chunk.getPageNumber() : chunk.getPageStart(),
                        "EXTRACTED", chunk.getQualityScore()))
                .toList();
    }

    private int ideaFactScore(ReproductionSpecDocument.FactItem fact, Set<String> terms) {
        return factPriority(fact) * 2
                + ideaTextScore(joinNonBlank(fact.getFactType(), fact.getKey(), fact.getValue()), terms) * 5;
    }

    private int ideaTextScore(String text, Set<String> terms) {
        String normalized = valueOrEmpty(text).toLowerCase();
        return (int) terms.stream().filter(normalized::contains).count();
    }

    private Set<String> ideaTerms(ResearchIdea idea) {
        String text = joinNonBlank(
                idea.getTitle(), idea.getResearchQuestion(), idea.getPossibleMethod(),
                idea.getInnovationPoints(), idea.getRefinedContent(), idea.getOriginalContent(), idea.getTags());
        Set<String> terms = new HashSet<>();
        for (String token : text.toLowerCase().split("[^\\p{L}\\p{N}_]+")) {
            if (token.length() >= 3) terms.add(token);
        }
        return terms;
    }

    private int factPriority(ReproductionSpecDocument.FactItem fact) {
        String type = valueOrEmpty(fact.getFactType()).toUpperCase();
        if (type.contains("HYPERPARAMETER") || type.equals("LOSS") || type.equals("DATASET")) return 5;
        if (type.contains("ALGORITHM") || type.equals("IMPLEMENTATION")) return 4;
        if (type.contains("ARCHITECTURE")) return 3;
        if (type.equals("EQUATION") || type.equals("PREPROCESSING")) return 2;
        return 1;
    }

    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (String token : raw.split(",")) {
            try { ids.add(Long.parseLong(token.trim())); } catch (NumberFormatException ignored) { }
        }
        return ids.stream().filter(id -> id > 0).distinct().toList();
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private String timestamp(LocalDateTime... values) {
        return Arrays.stream(values).filter(Objects::nonNull).findFirst().map(LocalDateTime::toString).orElse("unknown");
    }

    private String joinNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).map(String::trim).reduce((left, right) -> left + "\n\n" + right).orElse("");
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().map(String::trim).orElse("");
    }

    private int summaryPriority(PaperSectionSummary summary) {
        String type = valueOrEmpty(summary.getSectionType()).toUpperCase();
        String title = valueOrEmpty(summary.getSectionTitle()).toUpperCase();
        String combined = type + " " + title;
        if (combined.contains("METHOD") || combined.contains("MODEL") || combined.contains("ALGORITHM")) return 4;
        if (combined.contains("EXPERIMENT") || combined.contains("RESULT") || combined.contains("IMPLEMENT")) return 3;
        if (combined.contains("ABSTRACT") || combined.contains("INTRODUCTION")) return 2;
        if (combined.contains("REFERENCE") || combined.contains("CONFLICT") || combined.contains("AUTHOR")) return 0;
        return 1;
    }

    private String truncate(String value, int maxChars) {
        String normalized = valueOrEmpty(value);
        if (normalized.length() <= maxChars) return normalized;
        return normalized.substring(0, maxChars) + "\n[truncated by MyAgent context budget]";
    }

    private String valueOrEmpty(String value) { return value == null ? "" : value; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private String safeTitle(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
