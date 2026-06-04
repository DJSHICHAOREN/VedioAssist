# 电影英语助手 (Movie English Assistant)

一个 Android 应用，通过悬浮窗叠加在视频 App 上方，实时 OCR 识别屏幕字幕，结合字幕文件定位播放位置，内置 AI 语言模型提供单词、句子的中文解释，帮助中文用户在观影过程中高效学习英语。

## 项目结构

```
VideoAssist/
├── app/                      # Android 主应用（计划一）
│   ├── src/main/java/com/movieenglish/assistant/
│   │   ├── floating/         # 悬浮窗 + Compose UI 组件
│   │   ├── capture/          # 屏幕捕获（MediaProjection）
│   │   ├── ocr/              # OCR 推理引擎（TFLite）
│   │   ├── llama/            # llama 推理引擎（JNI）+ 预处理管线
│   │   ├── subtitle/         # 字幕解析 / 匹配 / 仓库
│   │   ├── vocabulary/       # 用户词库 + 生词等级调节
│   │   └── data/             # Room 数据库层
│   ├── src/main/cpp/         # JNI 桥接（llama.cpp）
│   └── src/main/assets/models/  # 模型文件目录
├── annotation-tool/          # 数据标注工具（计划二）
├── training/                 # OCR 训练管线（计划三）
├── llama-optimize/           # llama 优化管线（计划四）
└── docs/                     # 文档
    ├── superpowers/specs/    # 设计文档
    ├── superpowers/plans/    # 实现计划
    └── 使用教程.md            # 详细使用教程
```

## 功能

- **悬浮窗字幕识别**：半透明字幕条叠加在视频上，OCR 实时识别英文字幕
- **难词智能标记**：AI 预分析字幕，黄色下划线标记生词，蓝色标记已掌握词汇
- **单词解释**：点击单词弹出解释（音标、释义、词根词缀拆解、例句）
- **整句分析**：点击红点查看翻译、逐词拆解、语法分析
- **AI 对话**：内置 Qwen 2.5 语言模型，随时追问单词或句子
- **用户词库**：自动学习用户词汇量，点击越多标记越准
- **字幕定位**：OCR 文本匹配字幕文件，精确定位播放位置
- **数据标注工具**：独立的 Android App，框选字幕区域制作训练数据
- **模型训练管线**：CRNN OCR 模型训练 + 蒸馏 + 剪枝 + 量化
- **模型优化管线**：llama 模型剪枝 + GGUF 量化

## 技术栈

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose |
| 屏幕捕获 | MediaProjection API |
| OCR 推理 | TFLite (INT8 CRNN) |
| LLM 推理 | llama.cpp (GGUF, JNI) |
| LLM 模型 | Qwen 2.5 1.5B (Q4_K_M 量化) |
| 本地存储 | Room (SQLite) |
| 模型训练 | PyTorch + CUDA |
| 标注工具 | Android View + Canvas |

## 快速开始

### 前置条件

- Android Studio Hedgehog (2023.1) 或更新
- JDK 17
- Android SDK 34
- NDK（用于编译 JNI 桥接）
- GPU 服务器（用于模型训练，可选）

### 编译主应用

```bash
# 克隆项目
git clone <repo-url> && cd VideoAssist

# 编译
./gradlew :app:assembleDebug

# 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

> 首次编译需要将 OCR 模型 (`ocr_model.tflite`) 和 llama 模型 (`qwen2.5-1.5b-q4_k_m.gguf`) 放入 `app/src/main/assets/models/`。模型由计划三和计划四产出。暂无模型时，应用仍可运行（OCR 和 AI 功能不可用）。

### 编译标注工具

```bash
./gradlew :annotation-tool:assembleDebug
adb install annotation-tool/build/outputs/apk/debug/annotation-tool-debug.apk
```

### 完整使用教程

详见 [`docs/使用教程.md`](docs/使用教程.md)

## 设计文档

- [设计文档](docs/superpowers/specs/2025-06-03-movie-english-learner-design.md)
- [计划一：Android 主应用](docs/superpowers/plans/2025-06-03-plan-1-android-main-app.md)
- [计划二：数据标注工具](docs/superpowers/plans/2025-06-03-plan-2-annotation-tool.md)
- [计划三：OCR 训练管线](docs/superpowers/plans/2025-06-03-plan-3-ocr-training-pipeline.md)
- [计划四：llama 优化管线](docs/superpowers/plans/2025-06-03-plan-4-llm-optimization.md)

## 模型选择理由

### OCR：自训练 CRNN

通用 OCR（Tesseract、ML Kit）对电影字幕场景效果不佳——字幕字体多变、有描边阴影、背景是动态视频画面。自训练模型可以针对字幕场景优化，且通过蒸馏剪枝量化后体积仅 3MB。

### LLM：Qwen 2.5 1.5B

| 因素 | Qwen 2.5 1.5B | 其他候选 |
|------|-------------|---------|
| 中英双语 | 原生中英文训练，中文表达自然 | Llama/Phi 中文能力偏弱 |
| 模型大小 | 1.5B，Q4 量化后约 1.2GB | Phi 3.8B 量化后仍偏大 |
| 推理速度 | 手机端 10-20 token/s | 更大模型可能卡顿 |
| 生态兼容 | llama.cpp / MLC 均支持 | Gemma 生态略窄 |
| 开源协议 | Apache 2.0 | — |

## 权限说明

| 权限 | 用途 |
|------|------|
| 悬浮窗 | 显示字幕横条叠加在视频上 |
| 屏幕录制 | 捕获视频画面用于 OCR 识别 |
| 通知 | 前台服务保活所需（Android 13+） |
| 读取媒体 | 导入字幕文件 |
