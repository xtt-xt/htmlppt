# HTML PPT（Android 原生）

把网页版「纯文本 PPT」工具移植到 Android 原生 App。基于 **WebView + JsBridge**，
用 Markdown 子集 + `@` 指令编写幻灯片，支持多主题、分段进度条、单文件导出放映。

- 包名：`com.example.htmlppt`
- 数据目录：`/sdcard/HTML_PPT/`
- 语言：Java（Android SDK 35，minSDK 23）

---

## 技术栈 / 构建工具

| 部分 | 说明 |
|------|------|
| UI 宿主 | WebView + `MainActivity`（权限、返回键、全屏、edge-to-edge） |
| JS 桥 | `JsBridge`（文件/设置/主题/导出/系统 UI） |
| 文件系统 | `FileManager`（`/sdcard/HTML_PPT` 读写） |
| 构建 | 脚本 `/opt/build_apk.sh`：aapt2 → javac → d8 → zipalign → apksigner |
| SDK | android-35，build-tools 34.0.0 |

---

## 目录结构

```
app/src/main/
├── java/com/example/htmlppt/
│   ├── MainActivity.java      # WebView 宿主 / 权限 / 返回键 / 全屏
│   ├── JsBridge.java          # JS<->原生桥
│   └── FileManager.java       # /sdcard/HTML_PPT 读写
├── assets/www/                # 内置界面母本（首启动复制到 /sdcard/HTML_PPT/www）
│   ├── index.html             # 主页（PPT 列表）
│   ├── editor.html            # （旧壳，未被使用）
│   ├── settings.html          # 设置
│   ├── app.js                 # 主页公共脚本
│   ├── theme.css              # 主页/设置样式
│   └── README.md              # 使用说明（启动自动复制到数据目录根）
├── res/                       # 图标 mipmap / strings
└── AndroidManifest.xml        # 权限 + 图标
```

用户数据目录 `/sdcard/HTML_PPT/`：

```
www/        界面文件（热更新改这里）
PPT/        演示 .txt（每页用 --- 分隔）
themes/     自定义主题（.json / .css）
Download/   导出的单文件 HTML
settings.json  全局设置
README.md   使用说明（启动自动复制）
```

> 编辑器/放映/导出工具页：`/sdcard/Download/HTML_PPT/index.html`（主页点击「编辑/播放」跳转到这里）。

---

## 构建

```bash
bash /opt/build_apk.sh
# 产物：/tmp/build/HTML_PPT.apk
# 可安装：adb install -r /tmp/build/HTML_PPT.apk
```

脚本为 aapt2 **单次模式**，规避 qemu 下 aapt2 daemon IPC 超时。

---

## 热更新

- **界面页**：修改 `/sdcard/HTML_PPT/www/` 下的 `.html/.css/.js` 后重启 App。
- **工具页**：修改 `/sdcard/Download/HTML_PPT/index.html` 后重启 App。
- **原生 Java** 改动需重新 `build_apk.sh` 并重装。

---

## 语法

### 分页与结构
```
用 --- 分隔每一页
# 标题   ## 副标题   ### 小节
```

### 富文本
```
- 列表   1. 编号列表
> 引用
**加粗**  *斜体*  `代码`  [链接](url)
![说明](图片URL)    # 图片（本地路径导出时自动转 base64）
```

### @ 指令
```
@center           整页居中
@t(页脚文字)       页面页脚
@theme(主题名)     指定该页主题
@theme-def:名称 c1:... c2:... c3:... accent:... text:... muted:... pb:...
@theme-def-end     # 自定义主题（可写进度条颜色 pb）
@progress:show|top|颜色|段落|比例   # 分段进度条
|段落名|           # 从该页起进入新段落
```

### 主题
内置 6 个主题：`aurora / ocean / forest / sunset / mono / dawn`（见 `THEMES`）。
`@theme-def` 自定义主题：`c1/c2/c3`（背景渐变）、`accent`、`text`、`muted`、`pb`（进度条颜色）。

### 进度条
`@progress:show|top|颜色|段落|比例`
- 第 1 段 `show/hide`：是否显示
- 第 2 段 `top/bottom`：位置
- 第 3 段 颜色：`#hex` / 主题名 / `accent` / `default`
- 第 4 段 `1/0`：是否显示段落名
- 第 5 段 比例：如 `20,15,15,15,15,20`（各段宽度比）

---

## 导出

`buildExportHTML()` 生成**单文件可放映** HTML（不含编辑器），长按呼出「全屏 / 自定义主题」菜单。
- 网络图（http/https/data）原样保留；
- **本地图片**通过 `JsBridge.readImageBase64()` 自动转 base64 内嵌，独立可显示。

---

## 说明
- 工具页加载自 `/sdcard/Download/HTML_PPT/index.html` 的本地文件，可在 PC 上直接编辑该文件调试。
- `www/editor.html` 为历史残留壳（主页未引用）。
