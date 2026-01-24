# Snap2Action — ActionPlan 方案规范（v1.0）

## 0. 目标与原则

**目标**：将“屏幕/通知/分享”输入转化为可执行的动作卡片（Action），用户一滑确认后由系统能力落地执行（建日程/导航/待办等）。

**原则**

1. **稳定 JSON**：确保始终可解析，字段缺失可降级，不得导致崩溃。
2. **可解释**：每个动作必须返回证据（命中的文本片段），用于 UI 展示“解析依据”。
3. **可确认**：不确定的关键信息（时间/地点）必须显式标低置信度并触发确认。
4. **可去重**：同一来源重复触发不得重复执行（dedup_key 幂等）。
5. **可扩展**：后续新增 action 不重构现有结构；通过 `schema_version` 管理兼容。

---

## 1. 数据结构总览

ActionPlan 由 **Plan 顶层** 和 **Actions 列表** 组成：

* **Plan**：描述输入来源、原文、时区、生成时间、动作候选列表。
* **Action**：单张可执行卡片，包含 type、slots（参数）、置信度、证据、去重 key、执行前置与兜底策略。

---

## 2. ActionPlan 结构

### 2.1 Plan 顶层字段

* **schema_version**：版本号，当前固定为 `1.0`，后续升级时使用。
* **plan_id**：唯一标识符（UUID）。
* **created_at**：生成时间（ISO8601 格式）。
* **timezone**：时区（如 `Asia/Shanghai`）。
* **locale**：地区标识（如 `zh-CN`）。
* **source**：输入来源信息，包含应用包名、源 ID、标题和接收时间。
* **raw_text**：输入的原始文本或图像识别结果，供 UI 显示及证据回溯。
* **actions**：候选动作列表，最多包含 2-3 条建议。
* **summary**：简短摘要，便于 UI 展示。
* **debug**：调试信息（开发期使用，正式版关闭）。

### 2.2 Action 字段

* **action_id**：动作唯一标识符（UUID）。
* **type**：动作类型，如 `CREATE_CALENDAR_EVENT`、`NAVIGATE`、`ADD_TODO` 等。
* **title**：UI 展示的标题。
* **priority**：动作优先级，主建议为 1，备选为 2。
* **confidence**：动作整体置信度，范围 0~1。
* **requires_confirmation**：是否需要用户确认或编辑。
* **slots**：动作参数，根据不同类型动作变化（见后续描述）。
* **slot_confidence**：每个参数的置信度，用于 UI 显示。
* **evidence**：解析依据，包括文本片段、提取方法、证据评分等。
* **dedup_key**：去重标识符，确保同一来源不会重复触发同一动作。
* **preconditions**：执行前置条件，如权限要求、应用安装检查等。
* **fallback**：执行失败时的兜底策略，如提示用户补充信息或回退操作。

---

## 3. ActionType 和 Slots

### 3.1 ActionType（动作类型）

* `CREATE_CALENDAR_EVENT`
* `NAVIGATE`
* `ADD_TODO`
* `DRAFT_REPLY`
* `COPY_TEXT`
* `OPEN_URL`

### 3.2 每个 Action 的 Slots 设计

#### A) `CREATE_CALENDAR_EVENT`

* **必填**：`title`、`start_time`
* **可选**：`end_time`、`all_day`、`location_text`、`notes`

#### B) `NAVIGATE`

* **必填**：`destination_text`
* **可选**：`mode`（如 `driving`、`transit`、`walking`）

#### C) `ADD_TODO`

* **必填**：`title`
* **可选**：`due_time`、`notes`

#### D) `DRAFT_REPLY`

* **必填**：`reply_text`
* **可选**：`channel_hint`（如 `wechat`、`email`）、`copy_also`

#### E) `COPY_TEXT`

* **必填**：`text`

#### F) `OPEN_URL`

* **必填**：`url`

---

## 4. 置信度与确认策略

### 4.1 slot_confidence 规则

* **明确时间**（如 `2026-01-25 14:00`）→ 置信度 ≥ 0.80
* **相对时间**（如 `明天下午`）→ 置信度 0.70~0.80
* **模糊时间**（如 `下周找时间`）→ 置信度 ≤ 0.40

### 4.2 requires_confirmation 触发条件

* 任一 **required slot** 置信度 < 0.70
* 时间或地点无法解析为明确值
* 解析结果冲突时，需要确认用户意图
* 高风险操作（如删除或修改数据）需要确认

A 端 UI：若 `requires_confirmation=true`，默认显示编辑界面，要求用户确认信息。

---

## 5. 去重与幂等（dedup_key）

### 5.1 dedup_key 生成方式

* 基于动作类型、规范化后的标题、开始时间和来源信息生成唯一键（使用 sha256）。

### 5.2 C 端去重策略

* 若 dedup_key 已执行成功，则返回“已处理过”并跳过。
* 若上次执行失败，允许重试，但确保不重复创建动作。

---

## 6. 错误与降级要求

* B 端输出必须为合法 JSON，若 LLM 输出格式不正确，则回退至规则解析结果。
* 若无法生成有效的动作，返回 `actions=[]`，并在 `debug` 字段提供错误原因。
* A 和 C 端应处理未知的 action 类型，避免因错误而崩溃。

---

## 7. 示例

以下是一个完整的 ActionPlan 示例：

```json
{
  "schema_version": "1.0",
  "plan_id": "uuid",
  "created_at": "2026-01-24T10:12:30+08:00",
  "timezone": "Asia/Shanghai",
  "locale": "zh-CN",
  "source": { "type": "notification", "app_package": "com.tencent.mm", "source_id": "notif:xxx" },
  "raw_text": "明天下午两点在新主楼B座512开组会",
  "actions": [
    {
      "action_id": "uuid",
      "type": "CREATE_CALENDAR_EVENT",
      "title": "创建日程：组会（明天 14:00）",
      "priority": 1,
      "confidence": 0.78,
      "requires_confirmation": false,
      "slots": {
        "title": "组会",
        "start_time": "2026-01-25T14:00:00+08:00",
        "end_time": "2026-01-25T15:00:00+08:00",
        "location_text": "新主楼B座512"
      },
      "slot_confidence": { "title": 0.9, "start_time": 0.78, "location_text": 0.85 },
      "evidence": [
        { "source_field": "raw_text", "text_snippet": "明天下午两点", "extracted_as": "2026-01-25T14:00:00+08:00", "method": "rule" }
      ],
      "dedup_key": "sha256:..."
    }
  ]
}
```

---

## 8. 团队接口约定

* **B（Parser）**：输入 RawInput → 输出 ActionPlan(JSON)；确保 JSON 合规，返回完整证据、dedup_key，并处理字段缺失。
* **A（UI）**：渲染 actions 列表；按 `slot_confidence` 标黄；`requires_confirmation=true` 时，默认展示编辑页。
* **C（Executor）**：按 type 执行；先校验 required slots；执行前检查权限/安装；失败时回退至 fallback。

---

## 9. 扩展与迭代

* 后续可扩展更多动作类型（如查询快递、缴费等），但不需要修改现有结构。扩展时保证 `schema_version` 向后兼容。

---

此规范为开发过程中统一契约，确保跨团队协作的一致性与效率。如有疑问，请及时沟通。
