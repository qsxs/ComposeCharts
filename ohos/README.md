# ComposeCharts 1.0.0 OHOS 独立 producer

上游 [ehsannarmani/ComposeCharts 1.0.0](https://github.com/ehsannarmani/ComposeCharts/commit/8553b2bdbe209187fbba4fa3b567ce2fb978206a) 的作者为 Ehsan Narmani。配方保留 41 份 common 源与 1 份 native 源，包括完整公开 API 和 `pointerInteropFilter` 的 expect/actual；不修改上游 Kotlin 或原有 Android/iOS 构建。`source-inputs.json` 固定源文件与公开声明清单，`vendor/` 保留上游 sources JAR、POM、GMM 和构建资料原件。

Prepare 从调用者指定的 Fork 提交读取 Git blob，将与固定 sources JAR 及清单逐字节一致的源码写入隔离工作区再编译。Windows 的 `core.autocrlf=true` 可能使 Fork 工作树使用 CRLF；这些工作树字节不作为原始 LF 字节的证明，也不被静默改写。配方目录内的 `.gitattributes` 保持自身原始文本的 LF，JAR 保持二进制。

独立根坐标为 `io.github.qsxs.cpf.charts:compose-charts:1.0.0-cpf11-preview-<sourceSHA12>-<runID>-<attempt>`。固定 Kotlin/Native、KGP 和 Compose compiler `2.4.20-0.1.0-08`，Compose `1.12.0-0.1.0-11`，Gradle 9.5.0 和 JDK 17；commonMain 保持上游 Foundation `implementation` 与 KGP 默认 stdlib。标准 KGP metadata、OHOS x64 和 arm64 使用相同固定源集，不添加 metadata 后备或裁减未使用 API。

仓库根 [LICENSE](https://github.com/ehsannarmani/ComposeCharts/blob/8553b2bdbe209187fbba4fa3b567ce2fb978206a/LICENSE) 是 Apache-2.0；上游 POM 和模块发布配置声明 MIT，两类原件均保留，不能据此推断双许可。按已确认的决定，本移植的 POM 使用 Apache-2.0，并保留上游作者与仓库信息。

依次执行 `Plan`、`Prepare`、`Tasks`、`Resolve`、`Metadata`、`NativeX64`、`NativeArm64`、`Publish`、`Verify`。例如 Linux 中：

```sh
python -B ohos/ChartsPort.py Prepare --source-sha "$SOURCE_SHA" --run-id "$RUN_ID" --run-attempt "$RUN_ATTEMPT" --work "$WORK_DIR" --repository "$STAGING_DIR" --tooling "$TOOLING_DIR" --native-home "$NATIVE_HOME"
```

`SOURCE_SHA` 使用包含本配方的 Fork 完整提交 SHA；Plan 仅读取来源，Prepare 及后续阶段会核对配方文件与该提交的 Git blob 完全一致。各阶段其余参数保持一致。`--tooling` 指向公共工具仓根，省略时从固定的 `d0cff88fd1ea2e4f08bb4409f1c802cca0c7f285` 公开归档取得并核对工具文件。`--native-home` 指向调用者准备的官方 `.08` Kotlin/Native 目录；JDK 通过 `JAVA_HOME` 指定。工作区与 staging 必须相互分离，不能等于或包含配方目录；首次 Prepare 时两者必须不存在或为空。工作区内生成的 Gradle、Konan、临时文件和日志彼此隔离，staging 仅供本次发布。失败现场保留，失败阶段不可在同一工作区覆盖重试，请使用新的工作区、staging 和运行标识。已成功准备的目录可重复执行 Prepare 以复核身份。

阶段检查记录 41 份 metadata、各 42 份 Native 编译源，编译器插件及工具身份、实际产物和依赖解析；双 ABI 对旧配方的公开 metadata 名称和枚举生成 IR 签名清单进行检查。发布核对 root/leaf GMM、POM scope、完整 sources、原始资料、provenance 和校验和。上述检查属于配方要求，本阶段尚未执行 Gradle/Native、发布、公开消费、link 或 runtime；设备及 Android/iOS 回归均未验证。
