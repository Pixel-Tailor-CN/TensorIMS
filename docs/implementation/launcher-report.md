# 内置启动器实施报告

## 已交付

- `EmbeddedLauncher(context)` 实现公开的 `pair(port, code)`、`startWireless(port)`、`startRoot()`。`null` 仅在真正完成配对协议，或已收到本次挑战认证的私有 Binder 后返回；发送命令、shell 关闭和 `su` 退出本身不表示服务成功
- 配对为 Android 无线 ADB 的 TLS 1.3 + exporter 绑定 SPAKE2 + AES-GCM PeerInfo 交换。严格验证协议版本、阶段、载荷长度以及设备 GUID 类型；不是模拟返回成功
- 连接为真实 STLS 升级 + TLS 1.3 + CNXN/OPEN/WRTE/CLSE。拒绝明文 AUTH 回退；不调用远端主机，不自动打开无线调试，不复制官方授权和密钥
- 只有 `127.0.0.1`，外部不能传入 hostname。配对端口和连接端口独立；有界连接、读取、总操作超时，取消会关闭 Socket，等 I/O worker/native 上下文清理后释放互斥锁
- 独立 RSA-2048 ADB 身份仅在明确配对操作中生成，Android Keystore AES-256-GCM 包裹，AAD 独立，密文通过 AtomicFile 写入 `noBackupFilesDir/tensorims_adb_identity.v1`。连接时密钥缺失/失效不会静默生成新身份。重新配对允许更新失效身份，不清理业务备份
- 配对码不落存储/日志；可变派生缓冲区主动覆盖，Java String/PrivateKey 与上游 native 分配器不能承诺可验证的全内存擦除。没有为了测试生成真实设备或用户配对凭据
- 每次启动重新读取当前安装包，核对 package、UID、版本号/名称、可读规范 APK 路径和当前签名，生成 32 字节随机挑战。命令固定为当前 APK 的 `EmbeddedServerMain`，每个参数单引号转义，没有命令输入界面
- root 仅显式执行 `su -c` 固定命令。无线 ADB 仅执行同一命令。固定 `/system/bin/setsid /system/bin/app_process` 脱离 ADB shell 会话；stdin/stdout/stderr 定向 `/dev/null`。无按进程名 kill、官方服务清理、官方 starter 或 rish
- Launcher 自身防连续点击；UI 通过 `PrivilegeRuntime.startEmbedded` 持有与切换/操作共用的门。错误文字已分离到 `launcher_strings.xml` 的中英资源；日志仅阶段及异常类型，不记录载荷/私钥/配对码/完整命令

## 与私有桥接约定

入口参数固定六个，按顺序：`userId, expectedUid, versionCode, signerSha256LowerHex, challengeHex64, apkPath`。

启动前调用 `EmbeddedConnection.expect(challenge)`；成功必须满足 `acceptedChallenge()==challenge && current()!=null`。Provider/握手仍独立检查真实 Binder shell/root 调用者、当前安装身份与模式。失败/取消撤销挑战。与桥接负责人协调了认证已到达后取消/断线的资源清理：`clear()` 尝试关闭空闲 owned 实例，不能确认时保留引用；未经后续使用确认的握手另有有界租约。启动器不会把仍活着的私有实例按名称强杀。

## 依赖与 native 来源

- 整合者已负责添加 `org.bouncycastle:bcpkix-jdk18on:1.80.2` 目录版本/依赖、INTERNET，以及 `moe.shizuku.manager.adb.PairingContext` 的 R8 keep
- 当前 SDK 无 NDK，因此采用批准的官方预编译 fallback。唯一第三方保留包是官方 `RegisterNatives` 硬编码要求的最小 `PairingContext`；第一方逻辑全部位于 `app.mystery0.ims.tensor`
- 重新下载官方 release URL 得到同一 APK SHA-256，避免只信本地文件名
- 官方 APK：`6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f`
- 提取 arm64 `libadb.so`（90,792 bytes）：`a4257bc05ace812afa60a81e656724a07691edd08868dc90caab3bc6d0a49207`
- release commit：`2650830c5b099ae0dd34fedf614d4f592ca05d65`；审阅 commit：`b844bc491f1790c72328e1a8e5b2349f8978f0ea`。两处 `adb_pairing.cpp` 完全相同，SHA-256 `699139fb2f8aff139e877520f2e199fe4432ece0bf7fb3656ce0084484615baf`
- ELF 所有 LOAD 段为 `0x4000` 对齐，仅需要系统 liblog/libm/libdl/libc。没有新增 libc++_shared.so
- `assets/licenses/source-manifest.json` 固定 APK、来源 commit、native/许可/保留源码哈希。保留 Shizuku Apache-2.0、Shizuku API MIT、当时 BoringSSL OpenSSL/SSLeay/ISC/MIT 全许可、LLVM libc++ 许可、Bouncy Castle MIT 与 NOTICE
- `third_party/shizuku-adb` 为审阅/来源参考。未声称该部分源码目录能独立重建，也未声称官方二进制经本地可复现构建

## 测试与验证结果

先建立编译有效的待实现桩，11 个测试分两轮验证 RED → GREEN：

1. 初始 10 项全部失败：端口、六位 ASCII 配对码、逐字节部分读取、ADB checksum/长度/magic、EOF、线格式字节序、配对阶段/长度、固定命令转义与安装身份约束
2. 实现后 10 项通过。手工构造 WRTE 测试 fixture 曾将最后一个 command 字节写成 W 而不是 E，经独立对照 ASCII/协议修正；没有放宽生产校验
3. 新增 `setsid` 脱离会话回归测试，先见 1 项失败，再修改启动命令
4. 最新隔离 JVM/JUnit 执行：`OK (11 tests)`；见 `verification/launcher-unit-green.log`。RED 记录亦保存于同目录
5. Java 包装器经 JDK 21 `javac` 与 `javap -s -private` 检查六个 native 签名，和官方 JNI 注册逐项相符
6. native、保留原生源码、许可文件 SHA-256 清单逐项校验，中英 XML 解析通过；项目身份/单 APK 静态检查通过
7. 一次 `:app:compileDebugKotlin --offline` 在并行整合未完成时失败，未报本任务源码错误；实际阻塞为当时尚未存在的 PrivilegeRuntime，以及其他文件的 UserHandle.identifier/nullable onCreate 改造。记录 `/tmp/tensorims-launcher-compile.log` 已报告整合者。按整合者要求后续全量 Gradle/test/assemble/lint 由其统一运行，不能把这次失败描述为编译通过

常规复核命令：`./gradlew :app:testDebugUnitTest --tests '*AdbWireTest' --tests '*BootstrapCommandTest' --offline --console=plain`，随后全量测试、assembleDebug 和 lint。

## 真机验收仍未完成

本次没有模拟器/真机、没有真实配对/root 执行。下列项必须在 Android 13+ Pixel 真机上验收，不能以编译或 JVM 测试替代：系统 Conscrypt 隐藏 exporter 在目标版本可用；真实错误/过期配对码；系统撤销配对；16KB 页设备加载 JNI；root 管理器授权；启动后关闭无线调试；连接/配对时旋转或取消；应用升级/多用户的 APK 身份拒绝；双 Shizuku 服务共存与实际 IMS 回归。已知平台边界：首版 APK 路径限定 `/data/app/`，不支持 adopted-storage 的其他路径；ADB 自签名证书不按公网 CA 校验，安全边界依赖回环限制、配对的 TLS exporter/SPAKE2 和启动的真实 Binder UID 握手，不能宣称能防已控制 shell/root 的对手。

## 主要原始来源

- [Shizuku 固定原生源码](https://github.com/RikkaApps/Shizuku/blob/2650830c5b099ae0dd34fedf614d4f592ca05d65/manager/src/main/jni/adb_pairing.cpp)
- [官方 v13.6.0 release APK](https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk)
- [AOSP PeerInfo 协议](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/pairing_connection/include/adb/pairing/pairing_connection.h)
- [AOSP toybox setsid](https://android.googlesource.com/platform/external/toybox/+/40d21c59f76a9388b0ebb5c4c706732c2d034a67/toys/other/setsid.c)
- [BoringSSL 固定许可](https://github.com/google/boringssl/blob/571a7432a19592c620fa316abde47770dad4f82b/LICENSE)
