package com.myagent.assistant.researchengineering.service;

import com.myagent.assistant.researchengineering.git.AgentGitProjectDocument;
import com.myagent.assistant.researchengineering.git.AgentGitProjectStore;
import org.springframework.stereotype.Component;

/** Export only delivery coordinates, never the project document (which contains server paths). */
@Component
public class AgentDeliveryInstructions {
    private final AgentGitProjectStore store;

    public AgentDeliveryInstructions(AgentGitProjectStore store) {
        this.store = store;
    }

    public Delivery snapshot(String mode, long sourceId) {
        AgentGitProjectDocument project = store.find(mode, sourceId);
        if (project == null || !"GITEE".equals(project.getRemoteProvider())) {
            return unavailable("OFFLINE", "未绑定 Gitee，仅支持离线交付代码和检查报告；平台刷新不会接收这个 ZIP。需要远程同步时，请先准备外部任务并发布到 Gitee，再重新生成任务包。");
        }
        String branch = project.getAgentBranch();
        if (branch != null && branch.startsWith("agent/server/")) {
            return unavailable("PLATFORM_MANAGED", "当前是平台 Agent 分支，不允许外部 Agent 同时写入。请先在平台准备外部 Agent 任务并推送，再重新生成任务包；本包仅供离线参考。");
        }
        String repository = project.getRemoteSshUrl();
        // Generated Gitee SSH URLs have no embedded credentials. Reject anything else rather
        // than exporting a potentially secret-bearing URL or interpolating it into commands.
        if (repository == null || !repository.matches("git@gitee\\.com:[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+\\.git")
                || repository.contains("..") || branch == null
                || !branch.matches("agent/external/" + mode + "-" + sourceId + "-[A-Za-z0-9_-]+")
                || !commit(project.getBaselineCommit()) || !commit(project.getLatestCommit())) {
            return unavailable("INVALID_TARGET", "远程交付配置不完整或格式不安全，已隐藏地址并禁止推送。请在平台检查仓库、任务分支和版本记录后重新导出。");
        }
        boolean ready = "PUSHED".equals(project.getRemoteStatus()) || "SYNCED".equals(project.getRemoteStatus());
        return new Delivery("agent-delivery-v1", ready ? "GIT_REMOTE" : "REMOTE_NOT_READY", ready,
                repository, branch, project.getBaselineCommit(), project.getLatestCommit(), "origin",
                ready ? "仅向指定任务分支交付；完成推送后由用户在平台刷新接收。"
                        : "远程任务分支尚未确认推送成功，暂不能开始远程交付。请在平台重试推送成功后重新生成任务包。");
    }

    private boolean commit(String value) {
        return value != null && value.matches("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})");
    }

    private Delivery unavailable(String mode, String message) {
        return new Delivery("agent-delivery-v1", mode, false, "", "", "", "", "", message);
    }

    public record Delivery(String schemaVersion, String deliveryMode, boolean pushAllowed,
                           String repositoryUrl, String branch, String baselineCommit,
                           String expectedStartCommit, String cloneRemoteName, String message) {
        public String readme() {
            String introduction = "\n## 代码交付位置\n\n" + message + "\n\n"
                    + "机器可读说明位于 `task/delivery.json`，这是导出时的快照，不是实时授权。"
                    + "发布仓库、重试推送或更换任务后必须重新生成任务包，旧 ZIP 不会自动更新。\n"
                    + "任务包不携带 SSH 私钥、API Key、账号密码或服务器路径。外部电脑需要自行配置该仓库的访问与推送权限。\n";
            if (!pushAllowed) return introduction;
            return introduction + """

                    - 仓库地址：`%s`
                    - 唯一允许推送的任务分支：`%s`
                    - 任务基线（最初存档）：`%s`
                    - 导出时平台记录的版本：`%s`

                    ### 外部 Agent 操作步骤

                    1. 先让用户确认平台仍使用以上任务分支，且没有其他 Agent 同时写入；切换了任务请停止并重新下载。
                    2. 在一个新的空目录旁执行下面命令，克隆指定分支。`agent-workspace` 必须尚不存在。

                    ```text
                    git clone --single-branch --branch %s %s agent-workspace
                    cd agent-workspace
                    git merge-base --is-ancestor %s HEAD
                    git rev-parse HEAD
                    ```

                    第三条命令退出码必须为 0，表示远程代码包含预期起点；否则停止，不要强制回退。
                    记录最后一条命令输出为实际 baseCommit。克隆后的远程名为 origin，与服务器远程别名无关。

                    3. 阅读本包的任务与证据，在克隆仓库内修改代码。不要把整个 ZIP 覆盖到仓库，也不要覆盖 `.git`、已有代码或密钥配置。
                    4. 运行短测试，在 handoff 中记录实际 baseCommit、修改文件和测试结果。检查 `git status`，只暂存审核后的代码与报告，不能提交密钥、数据集或依赖目录；然后执行 `git commit` 保存修改。
                    5. 用 `git rev-parse HEAD` 记录 resultCommit，并向下面唯一的目标推送：

                    ```text
                    git push origin HEAD:refs/heads/%s
                    ```

                    推送前确认当前分支仍与 delivery.json 一致、origin 地址仍是指定仓库；不得推送 main，不得使用 --force。
                    权限失败或推送被拒绝时停止并报告，不要覆盖远程记录。推送成功后，用户在平台点击“刷新外部 Agent 分支”接收代码。
                    平台有未提交修改或两端提交出现分歧时会拒绝同步，需要人工处理。
                    """.formatted(repositoryUrl, branch, baselineCommit, expectedStartCommit,
                    branch, repositoryUrl, expectedStartCommit, branch);
        }
    }
}
