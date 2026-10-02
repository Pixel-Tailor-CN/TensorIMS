#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
CACHE=${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1
# 可选的离线纯逻辑快检；先以 Gradle 下载依赖，或显式设置现有 GRADLE_USER_HOME。
[ -d "$CACHE" ] || { echo "Gradle cache not found; run :app:testDebugUnitTest first" >&2; exit 2; }
CP=$(find "$CACHE" -name '*.jar' | grep -E 'kotlin-(compiler-embeddable|stdlib|script-runtime|reflect)|kotlinx-coroutines-core-jvm|annotations/|trove4j|junit/4|hamcrest-core' | paste -sd: -)
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
# 此快检只编译无 Android/BuildConfig 依赖的策略；双 flavor 身份测试由 Gradle 执行。
SOURCES=("$ROOT/app/src/test/java/app/mystery0/ims/tensor/bridge/BridgePolicyTest.kt")
POLICY_SOURCE=${BRIDGE_POLICY_SOURCE:-$ROOT/app/src/main/java/app/mystery0/ims/tensor/bridge/BridgePolicy.kt}
if [ -f "$POLICY_SOURCE" ]; then SOURCES+=("$POLICY_SOURCE"); fi
java -Xmx256m -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$CP" -d "$OUT" "${SOURCES[@]}"
java -Xmx256m -cp "$OUT:$CP" org.junit.runner.JUnitCore app.mystery0.ims.tensor.bridge.BridgePolicyTest
