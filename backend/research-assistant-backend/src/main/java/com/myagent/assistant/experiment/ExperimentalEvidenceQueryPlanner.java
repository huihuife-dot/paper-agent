package com.myagent.assistant.experiment;

import com.myagent.assistant.rag.dto.EvidenceQueryPlan;
import com.myagent.assistant.rag.service.EvidenceQueryPlanner;
import com.myagent.assistant.rag.service.impl.EvidenceQueryPlannerImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.List;

/** 默认原样委托旧实现，只有受保护实验上下文选择 JEV 才走扩展。 */
@Service
@Primary
public class ExperimentalEvidenceQueryPlanner implements EvidenceQueryPlanner {
    private final EvidenceQueryPlannerImpl baseline;
    private final JevRouteClient jev;
    public ExperimentalEvidenceQueryPlanner(EvidenceQueryPlannerImpl baseline, JevRouteClient jev) {
        this.baseline = baseline; this.jev = jev;
    }
    @Override public EvidenceQueryPlan plan(String question, List<Long> ids) {
        if (!ExperimentTrace.active()) return baseline.plan(question, ids);
        try (var ignored = ExperimentTrace.stage("routing")) {
            ExperimentTrace trace = ExperimentTrace.current();
            EvidenceQueryPlan result;
            if (trace.variant == ExperimentTrace.Variant.BASELINE) result = baseline.plan(question, ids);
            else {
                result = baseline.rulePlan(question, ids);
                if (baseline.shouldUseModel(question, result)) {
                    try {
                        result = baseline.mergeModelRoute(result, jev.route(question, result));
                        result.setRouterSource("JEV");
                    } catch (RuntimeException e) {
                        if (Thread.currentThread().isInterrupted()) throw e;
                        trace.fallbacks.add(e.getMessage() != null && e.getMessage().matches("JEV_[A-Za-z0-9_]+")
                                ? e.getMessage() : "JEV_FAILED");
                        result = baseline.plan(question, ids);
                    }
                }
            }
            trace.plan = result;
            return result;
        }
    }
}
