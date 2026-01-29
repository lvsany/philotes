# OCR功能 - 快速开始

## 🚀 立即开始

### Step 1: 同步Gradle依赖

在Android Studio中:
1. 点击工具栏上的 **Sync Project with Gradle Files** 按钮（🐘图标）
2. 等待依赖下载完成（约1-2分钟）

或在终端执行:
```bash
cd "/Users/lv.sany/Documents/Uni_workplace/sci/25软创/philotes"
./gradlew build --refresh-dependencies
```

### Step 2: 测试OCR功能

#### 方式1: 悬浮球截图
1. 运行应用（真机，Android 11+）
2. 开启辅助功能权限（设置→辅助功能→Philotes助手）
3. 点击悬浮球触发截图
4. 等待OCR识别（2-3秒）
5. 自动跳转到主界面，可进行AI解析

#### 方式2: 分享图片
1. 从相册或其他应用选择一张包含文字的图片
2. 点击"分享"→选择"Philotes"
3. 应用显示图片预览
4. 等待OCR识别（2-3秒）
5. 识别结果填入输入框，可编辑后点击"AI解析"

### Step 3: 查看识别结果

识别结果格式示例:
```
=== 屏幕内容识别 ===
图片尺寸: 1080 x 1920

[顶部-中] 项目会议通知
[中部-左] 时间：明天下午3点
[中部-左] 地点：A栋501会议室
[底部-中] 请准时参加

=== 纯文本内容 ===
项目会议通知
时间：明天下午3点
地点：A栋501会议室
请准时参加
```

## 📋 功能特性

### ✅ 已实现
- [x] ML Kit Text Recognition集成
- [x] 9宫格位置信息标注
- [x] 悬浮球截图自动识别
- [x] 分享图片识别
- [x] 异步处理不阻塞UI
- [x] 错误处理和友好提示
- [x] 结构化文本输出
- [x] 自动跳转AI解析流程

### 🎯 核心优势
1. **完美适配端侧模型**: Gemma 2B通过位置信息理解图片布局
2. **免费且离线**: ML Kit首次下载后完全离线工作
3. **用户可编辑**: OCR结果可编辑，容错性强
4. **准确度高**: Google ML Kit对中英文识别准确率95%+

## 🔧 配置说明

### 依赖项
```kotlin
// gradle/libs.versions.toml
mlkit-text-recognition = "16.0.0"

// app/build.gradle.kts
implementation(libs.mlkit.text.recognition)
```

### 权限要求
- ✅ 无需额外权限（ML Kit自动处理）
- ✅ 网络权限（首次下载模型，约10MB）
- ✅ 辅助功能权限（悬浮球截图）

## 📱 系统要求

- **最低Android版本**: Android 7.0 (API 24)
- **推荐Android版本**: Android 11+ (API 30+，支持截图功能)
- **网络要求**: 首次使用需联网下载模型（10MB）

## 🐛 常见问题

### Q1: IDE显示"Cannot resolve symbol 'MlKitOcrService'"
**A**: 需要同步Gradle依赖。点击 Sync Project with Gradle Files。

### Q2: 首次使用提示"OCR识别失败"
**A**: ML Kit需要首次下载模型文件（10MB），请确保：
1. 设备已连接网络
2. 等待10-15秒让模型下载完成
3. 重试识别

### Q3: 识别不出文字
**A**: 检查：
1. 图片中是否有清晰的文字
2. 文字是否太小或太模糊
3. 尝试不同角度或更清晰的截图

### Q4: 识别结果不准确
**A**: 用户可以：
1. 在输入框中手动编辑识别结果
2. 重新截图或选择更清晰的图片
3. 确保文字对比度足够（黑白分明效果最好）

## 📖 代码示例

### 使用MlKitOcrService
```java
Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

MlKitOcrService.recognizeTextAsync(bitmap, new MlKitOcrService.OcrCallback() {
    @Override
    public void onSuccess(OcrResult result) {
        String structuredText = result.toStructuredText();
        String plainText = result.getPlainText();
        int blockCount = result.getTextBlocks().size();
        
        // 处理识别结果...
    }
    
    @Override
    public void onError(Exception e) {
        Log.e(TAG, "OCR failed", e);
        // 错误处理...
    }
});

bitmap.recycle(); // 记得释放内存
```

## 🎨 UI流程图

```
用户操作
    ↓
[点击悬浮球] → [系统截图] → [保存到缓存]
    ↓
[显示"识别中..."] → [ML Kit OCR处理]
    ↓
[成功] → [显示结果] → [跳转主界面]
    ↓
[填充输入框] → [用户编辑] → [AI解析]
    ↓
[生成ActionPlan] → [执行动作]
```

## 📚 相关文档

- 详细实现指南: [OCR_IMPLEMENTATION_GUIDE.md](OCR_IMPLEMENTATION_GUIDE.md)
- 完整实现报告: [OCR_IMPLEMENTATION_COMPLETE.md](OCR_IMPLEMENTATION_COMPLETE.md)
- ML Kit官方文档: [Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition)

## 🎉 开始使用

现在就同步Gradle依赖，开始测试OCR功能吧！

```bash
# 在项目根目录执行
./gradlew build --refresh-dependencies
```

---

**需要帮助?** 查看详细实现指南或提Issue
