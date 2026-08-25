# VerbalKiller

<p align="center">
  <img src="design/app-icon-final.svg" width="128" alt="VerbalKiller app icon">
</p>

<p align="center">
  一个专注于 GRE「六选二」同义词题型的 Android 强化练习应用。
</p>

VerbalKiller 使用 Kotlin 与 Jetpack Compose 开发。项目内置 905 个 GRE 同义词组，通过短时、高频、题数驱动的重复机制，帮助使用者快速建立词义配对反应，而不是制定按天执行的学习计划。

## 题型说明

每道题提供 6 个单词，其中包含两组同义词和两个干扰项。使用者需要：

1. 在 A 中选择一组同义词（2 个）；
2. 在 B 中选择另一组同义词（2 个）；
3. 提交答案并查看逐组选词反馈及中文释义。

A、B 只用于区分两组答案，两组同义词的顺序可以互换。题目生成器会保证选项去重，并避免目标词组与干扰词组之间出现词面重叠。

## 核心功能

### 三种练习模式

| 模式 | 用途 |
| --- | --- |
| 智能练习 | 维护 24 个词组的活跃池，优先完成当前强化词组，通过后补入未练词组 |
| 错词强化 | 集中练习尚在强化中或累计答错至少 2 次的词组 |
| 随机练习 | 从完整词库随机出题，同时正常更新强化进度 |

切换模式不会丢失已经排入队列的强化任务。每次强化重现都会重新生成干扰项和选项顺序，避免仅凭题面位置记忆答案。

### 轮内强化机制

本项目面向短时突破和大量重复，因此不采用传统的 1、3、7、14 天间隔复习。重复间隔以“中间经过多少道题”计算：

| 当前结果 | 强化进度 | 再次出现 |
| --- | ---: | ---: |
| 答错 | 重置为 0/3 | 隔 3 题 |
| 连续答对第 1 次 | 1/3 | 隔 6 题 |
| 连续答对第 2 次 | 2/3 | 隔 12 题 |
| 连续答对第 3 次 | 本轮通过 | 本轮不再主动重现 |

中途答错会清空该词组的连续正确进度，并从“隔 3 题”重新开始。累计答错达到 2 次后，该词组会被标记为“高频错词”。

### 强化进度

进度页面提供：

- 全部、未练、强化中、本轮通过、高频错词五种筛选；
- 按单词、中文释义或词组编号搜索；
- 总体、近 7 天和近 30 天作答正确率；
- 常见混淆词统计；
- 每个词组的正确次数、强化进度和累计错误信息。

这里的“本轮通过”表示连续答对 3 次，是本轮强化状态，不代表永久掌握。正确率按实际作答记录计算，也不等同于长期记忆保持率。

### 本地数据

- 内置词库位于 `app/src/main/res/raw/words.csv`；
- 词组、作答记录、强化状态、混淆记录和词库元数据均由 Room 保存在本机；
- 词库导入使用 SHA-256 判断内容是否变化，并在更新内置词库时保留用户进度；
- 数据库包含从 v1 到 v2 的迁移，不会因升级直接清空已有记录；
- 应用不要求账号，也不依赖网络服务。

## 技术栈

- Kotlin 1.9
- Jetpack Compose + Material 3
- Navigation Compose
- Room 2.6
- Kotlin Coroutines / Flow
- Gson（兼容旧版持久化数据）
- Gradle 8.6 / Android Gradle Plugin 8.4
- JUnit 4、AndroidX Test 与 Espresso

## 环境要求

- JDK 17
- Android Studio（建议使用当前稳定版）
- Android SDK 34
- Android 8.1（API 27）或更高版本的设备/模拟器

项目通过 Kotlin JVM Toolchain、Java/Kotlin 编译选项和 `.java-version` 固定使用 JDK 17。Android Studio 中仍需确认 **Gradle JDK** 设置为 JDK 17。

## 快速开始

克隆项目后进入仓库：

```bash
git clone git@github.com:ShinkaiKung/VebalKiller.git
cd VebalKiller
```

首次打开项目时，Android Studio 通常会自动生成本机专用的 `local.properties`。如需手动配置：

```properties
sdk.dir=/path/to/Android/sdk
```

然后在 Android Studio 中完成 Gradle Sync，选择 API 27 以上的模拟器或设备运行 `app`。也可以使用命令行构建：

```bash
./gradlew assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接的设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 测试与质量检查

运行 JVM 单元测试：

```bash
./gradlew testDebugUnitTest
```

运行 Android Lint：

```bash
./gradlew lintDebug
```

启动模拟器或连接设备后运行设备端测试：

```bash
./gradlew connectedDebugAndroidTest
```

一次执行主要检查与构建：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

当前测试覆盖题目生成、严格判题、强化调度、模式候选策略、进度筛选、CSV 导入、持久化编解码、作答事务逻辑及数据库迁移冒烟检查。

## Release 构建

```bash
./gradlew assembleRelease
```

Release 已启用 R8 代码压缩和资源收缩。仓库不包含签名密钥，因此默认产物是：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

正式分发前，请在本机或 CI 中配置自己的签名信息。Android Studio 本地导出的 `app/release/` 目录已被 Git 忽略。

## 词库格式

`words.csv` 每行表示一个同义词组：

```csv
编号,主词,同义词1; 同义词2,中文释义
```

示例：

```csv
1,mitigate,abate; curtail; temper; ameliorate,缓和
```

要求：

- 编号必须是唯一整数；
- 每组至少包含两个去重后的英文词；
- 多个同义词使用分号分隔；
- 字段中如包含英文逗号，应使用标准 CSV 双引号包裹；
- 应保持 UTF-8 编码。

应用启动时会验证词库；格式错误会显示初始化失败信息，而不会静默导入不完整数据。

## 项目结构

```text
app/src/main/java/com/github/ShinkaiKung/verbalkiller/
├── domain/                 # 六选二题目模型、生成、判题与轮内强化调度
├── practice/               # 练习页 UI、状态及交互逻辑
├── info/                   # 强化进度页、统计、搜索与筛选
├── logic/
│   └── persistence/        # Room 实体、DAO、仓库、导入与数据库迁移
├── ui/theme/               # Compose 主题
├── MainActivity.kt
├── NavLayout.kt
└── VerbalKillerApplication.kt

app/src/main/res/raw/words.csv    # 内置词库
design/app-icon-final.svg         # 应用图标矢量源文件
```

## 设计边界

VerbalKiller 当前刻意保持单一目标：用较短时间反复训练 GRE 六选二同义词识别。它没有每日任务、打卡、通知、云同步或跨设备账号系统。若后续扩展，建议优先考虑词库导入导出、练习数据备份以及更细的错因分析，同时保留当前低操作成本的练习流程。

## 隐私

所有词库进度与作答记录默认只保存在应用本地。项目当前未声明网络权限，也没有接入遥测、广告或第三方账号服务。

## License

仓库目前尚未提供开源许可证。在添加 `LICENSE` 文件前，源代码及词库不自动获得开源使用授权。
