# OCR 实现指南

## 概述

本项目使用 **ML Kit Text Recognition** 实现图片文字识别功能。采用方案A（两步法）：OCR提取文本 + AI解析，并通过保留文本位置信息来增强AI对图片内容的理解。

## 实现方案

### 方案选择：ML Kit OCR + 位置信息增强

- **OCR引擎**：Google ML Kit Text Recognition
- **文本增强**：保留文本块的空间位置（9宫格定位）
- **AI模型**：端侧 Gemma 2B 或云端 API（OpenAI/DeepSeek）

### 为什么选择这个方案？

1. **兼容性**：端侧Gemma模型不支持多模态，只能用文本输入
2. **位置信息**：通过文本描述保留空间布局（如"顶部-左：标题"）
3. **免费且离线**：ML Kit首次下载模型后可离线使用
4. **准确度高**：Google ML Kit对中英文识别准确率都很好

## 核心组件

### 1. OcrResult 数据类
**文件**: `app/src/main/java/com/example/philotes/data/model/OcrResult.java`

存储OCR识别结果，包括：
- 文本内容
- 边界框坐标（Rect）
- 置信度
- 图片尺寸

**关键方法**：
- `toStructuredText()`: 生成带位置描述的结构化文本
- `getPlainText()`: 获取纯文本内容（不含位置信息）

### 2. MlKitOcrService 工具类
**文件**: `app/src/main/java/com/example/philotes/utils/MlKitOcrService.java`

封装ML Kit Text Recognition API：
- `recognizeTextAsync()`: 异步识别图片文本
- 回调接口 `OcrCallback`: 处理成功/失败结果

### 3. 集成点

#### FloatingButtonService（悬浮球截图）
**文件**: `app/src/main/java/com/example/philotes/FloatingButtonService.java`
- 截图后自动OCR识别
- 显示识别结果
- 跳转到MainActivity进行AI解析

#### MainActivity（主界面）
**文件**: `app/src/main/java/com/example/philotes/MainActivity.java`
- 处理分享的图片
- OCR识别并填充到输入框
- 用户可编辑后点击解析

## 位置信息增强策略

### 9宫格定位系统

图片被划分为9个区域：
```
顶部-左  顶部-中  顶部-右
中部-左  中部-中  中部-右
底部-左  底部-中  底部-右
```

### 结构化文本格式

```
=== 屏幕内容识别 ===
图片尺寸: 1080 x 1920

[顶部-中] 会议通知
[中部-左] 时间：明天下午3点
[中部-左] 地点：A栋501会议室
[底部-中] 请准时参加

=== 纯文本内容 ===
会议通知
时间：明天下午3点
地点：A栋501会议室
请准时参加
```

### AI如何理解位置信息

通过位置标签，Gemma模型可以：
1. 识别标题（通常在顶部）
2. 理解内容层次（中部为主要内容）
3. 区分主次信息（底部常为备注）
4. 推断逻辑关系（左右位置关系）

## 使用流程

### 1. 悬浮球截图识别
1. 用户点击悬浮球
2. 系统截图
3. ML Kit OCR识别文本
4. 显示识别结果
5. 自动跳转MainActivity
6. 用户点击"AI解析"生成ActionPlan

### 2. 分享图片识别
1. 用户从其他应用分享图片到Philotes
2. MainActivity显示图片预览
3. ML Kit OCR识别文本
4. 结构化文本填充到输入框
5. 用户可编辑后点击"AI解析"

## 依赖配置

### gradle/libs.versions.toml
```toml
[versions]
mlkit-text-recognition = "16.0.0"

[libraries]
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkit-text-recognition" }
```

### app/build.gradle.kts
```kotlin
dependencies {
    implementation(libs.mlkit.text.recognition)
}
```

## 注意事项

### 1. 首次使用需要下载模型
ML Kit首次使用会自动下载约10MB的OCR模型，需要：
- 网络连接
- 约10秒下载时间
- 下载失败会回调 `onError()`

### 2. 权限要求
- 无需额外权限（ML Kit自动处理）
- 网络权限已在AndroidManifest.xml中声明

### 3. 性能考虑
- OCR识别耗时：1-3秒（取决于图片大小）
- 建议异步调用，显示进度提示
- 识别完成后记得 `bitmap.recycle()` 释放内存

### 4. 识别准确度
- 清晰文本：95%+
- 手写文字：支持但准确度较低
- 复杂背景：可能影响识别
- 倾斜文字：ML Kit会自动矫正

## 错误处理

常见错误及解决方案：

1. **模型下载失败**
   - 检查网络连接
   - 重试识别
   - 提示用户手动输入

2. **未识别到文字**
   - 检查图片质量
   - 提示用户重新截图
   - 提供手动输入选项

3. **识别结果不准确**
   - 用户可在输入框中编辑
   - 保留原图供用户参考

## 后续优化建议

1. **缓存机制**：相同图片不重复识别
2. **批量识别**：支持多张图片连续识别
3. **自定义模型**：训练特定场景的OCR模型
4. **Vision API备选**：为云端用户提供多模态API选项

## 测试建议

测试场景：
- [ ] 清晰的截图（会议通知、日程安排）
- [ ] 包含时间地点的文本
- [ ] 倾斜的照片
- [ ] 复杂背景的文字
- [ ] 中英文混合文本
- [ ] 网络断开时的降级处理

---

**实施日期**: 2026-01-30  
**版本**: v1.0  
**状态**: ✅ 已完成实现
