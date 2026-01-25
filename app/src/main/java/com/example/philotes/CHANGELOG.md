# Changelog

所有重要变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

---

## [Unreleased]

### 2026-01-26 - ActionPlan 整合与重构

#### Added 新增
- 创建 `ActionExecutor` 统一执行器类
  - 统一处理所有类型的 ActionPlan
  - 根据 ActionType 自动路由到对应的 Helper
  - 标准化的 ExecutionResult 返回结果
  - 提供 `getActionSummary()` 方法用于 UI 展示

#### Changed 变更
- **重构 CalendarHelper**
  - 接受 `ActionPlan` 参数替代硬编码数据
  - 支持 ISO 8601 时间格式解析 (`YYYY-MM-DDTHH:MM:SS`)
  - 从 ActionPlan.slots 动态提取 title、time、location、content
  - 移除所有硬编码常量（EVENT_TITLE、EVENT_LOCATION 等）

- **重构 NavigationHelper**
  - 接受 `location` 字符串参数
  - 支持多地图应用（高德、百度、Google Maps）
  - 使用通用 `geo:` URI 作为兜底方案
  - 移除硬编码的目的地数据（DESTINATION_NAME、DESTINATION_LAT 等）

- **重构 TodoHelper**
  - 接受 `ActionPlan` 参数
  - 从 ActionPlan.slots 动态提取 title 和 content
  - 移除硬编码的待办数据（TODO_TITLE、TODO_DESCRIPTION 等）

- **重写 MainActivity**
  - 整合 ActionParser 和 ActionExecutor 工作流
  - 实现完整的 "解析 → 确认 → 执行" 流程
  - 添加执行前确认对话框，显示动作摘要
  - 改进权限处理逻辑，支持延迟执行
  - 三个快捷按钮现在填充示例文本后调用解析器

#### Removed 移除
- 移除 CalendarHelper 中的硬编码事件数据
- 移除 NavigationHelper 中的硬编码目的地数据
- 移除 TodoHelper 中的硬编码待办数据
- 移除 MainActivity 中直接调用 Helper 的旧代码
- 删除不再使用的 `getEventSummary()`、`getDestinationSummary()`、`getTodoSummary()` 等方法

#### Technical Details 技术细节

**工作流程**
```
用户输入 → ActionParser → ActionPlan → 确认对话框 → ActionExecutor → Helper → 系统操作 → 结果反馈
```

**ActionPlan 数据结构**
```json
{
  "type": "CREATE_CALENDAR | NAVIGATE | ADD_TODO | COPY_TEXT",
  "slots": {
    "title": "会议",
    "time": "2026-01-27T14:00:00",
    "location": "望京SOHO"
  },
  "originalText": "明天下午两点在望京SOHO开会",
  "confidence": 0.85
}
```

**文件变更清单**
- 新增: `domain/ActionExecutor.java`
- 修改: `MainActivity.java`
- 修改: `helper/CalendarHelper.java`
- 修改: `helper/NavigationHelper.java`
- 修改: `helper/TodoHelper.java`

#### Benefits 改进收益
- ✅ 彻底移除硬编码，实现真正的动态数据驱动
- ✅ 统一的执行接口，易于扩展新的 ActionType
- ✅ 完善的错误处理和用户反馈机制
- ✅ 智能权限管理，在需要时才请求
- ✅ 用户确认机制，防止误操作

#### Notes 注意事项
- ActionParser 必须输出 ISO 8601 格式的时间字符串
- 日历操作需要 `READ_CALENDAR` 和 `WRITE_CALENDAR` 权限
- 导航功能依赖用户设备安装的地图应用
- 执行前会显示确认对话框，用户可取消操作

---

## 历史版本

### 之前的工作
- ActionParser 实现（文本 → ActionPlan）
- Helper 类实现（系统操作的底层封装）
- 基础 UI 框架搭建
- LLM 模型集成
