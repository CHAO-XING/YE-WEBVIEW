# YE-WEBVIEW

基于 Android 系统 WebView 的极简浏览器：无广告、无信息流，内置 AI 网页摘要与问答。

## 功能
- **地址栏/搜索栏合一**：输入网址直接打开，输入关键词则搜索；搜索引擎可选 Bing / Google / 百度 / DuckDuckGo / 自定义模板（`%s` 代表关键词）。
- **多标签页**、**书签**、**历史记录**（本地 SharedPreferences + JSON 存储）。
- **广告拦截**：内置小型默认域名列表；可在设置中从 URL 导入 EasyList（解析 `||domain^` 规则），在 `shouldInterceptRequest` 中按域名及其父域名匹配拦截；可开关。
- **干净主页**，工具栏直接进入设置。
- **AI 按钮**：用 `evaluateJavascript` 提取页面正文（优先 article/main，否则取最大文本块，截断至 12000 字），发送到可配置的 OpenAI 兼容接口，在底部弹窗中显示摘要并支持追问。
- **朗读**：选中文字出现「朗读」菜单；更多菜单「朗读本页」抽取正文后走香港 edge-tts 反代合成 MP3，系统 MediaPlayer 播放。设置中可配 Base URL / Bearer Token / Voice。

## 下载 APK
- 最新调试版（Releases）：https://github.com/CHAO-XING/YE-WEBVIEW/releases/download/latest-debug/app-debug.apk
- 每次构建的产物：在 [Actions](https://github.com/CHAO-XING/YE-WEBVIEW/actions) 中打开对应运行，下载 `YE-WEBVIEW-debug-apk` 工件（需登录 GitHub）。

## 安装
1. 下载 `app-debug.apk` 到手机。
2. 打开时按提示允许"安装未知来源应用"（设置 → 应用 → 特殊权限 → 安装未知应用 → 允许浏览器/文件管理器）。
3. 安装完成后打开"YE浏览器"。

## 配置 AI
工具栏 ⚙ 设置 → "AI（OpenAI 兼容接口）"：
- Base URL：默认 `https://api.deepseek.com`（自动拼接 `/v1/chat/completions`）
- 模型：默认 `deepseek-chat`
- API Key：填入你的 Key，仅保存在本机
也可改为 OpenAI、Moonshot、本地 Ollama 等任何 OpenAI 兼容服务。

## 配置朗读（edge-tts）
设置 → 「朗读（edge-tts）」：
- TTS Base URL：默认 `https://work.yebrewing.com/edge-tts`（自动请求 `/tts`）
- Bearer Token：填入你的 Token，仅保存在本机（不要提交到仓库）
- Voice：默认 `zh-CN-XiaoxiaoNeural`
网页选中文字后点系统选区菜单「朗读」，或更多菜单「朗读本页」。

## 本地构建
需要 JDK 17 和 Android SDK（compileSdk 34）：
```bash
git clone https://github.com/CHAO-XING/YE-WEBVIEW.git
cd YE-WEBVIEW
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

## 路线图
- [ ] AI 操作网页（自动点击、填表、多步任务）
- [ ] 标签页/书签同步与导入导出
- [ ] 元素隐藏规则（EasyList `##` 规则）与定时更新订阅
- [x] 朗读（选区 / 本页 + edge-tts）
- [ ] 阅读模式、夜间模式、下载管理
- [ ] 正式签名的 Release 版本
