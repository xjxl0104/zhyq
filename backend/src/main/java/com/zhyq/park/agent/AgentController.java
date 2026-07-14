package com.zhyq.park.agent;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI Agent 扩展点位(批次① 地基,只留接口位不实现)。
 *
 * <p>对标共识:头部产品的 AI 内嵌在既有业务动作旁,而非独立"AI 模块"。本控制器仅预留:</p>
 * <ol>
 *   <li>{@code POST /api/agent/chat} —— 问答网关占位,当前返回未启用提示,后期接 agent 不改前端契约;</li>
 *   <li>{@code GET  /api/agent/metrics} —— 统一指标 API 占位,将来收拢各模块分散的 /stats 供 agent 问数;</li>
 *   <li>建议卡:复用 {@code sys_todo},约定 {@code bizType="suggestion"},由 agent 侧写入、前端"待办"渲染为可一键转工单的建议卡(本轮仅约定,不新增表)。</li>
 * </ol>
 */
@Tag(name = "AI 扩展点(占位)")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    /** 问答网关占位:接收消息,当前统一返回"未启用",保持契约稳定。 */
    @Operation(summary = "AI 问答网关(占位,未启用)")
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody(required = false) Map<String, Object> body) {
        return Result.ok("AI 助手尚未启用,接口位已预留", Map.of(
                "enabled", false,
                "echo", body == null ? Map.of() : body
        ));
    }

    /** 统一指标 API 占位:将来收拢 dashboard/energy/iot 等分散统计,供 agent 与大屏统一取数。 */
    @Operation(summary = "统一指标 API(占位)")
    @GetMapping("/metrics")
    public Result<List<Object>> metrics() {
        return Result.ok("指标 API 位已预留,尚未收拢各模块统计", List.of());
    }
}
