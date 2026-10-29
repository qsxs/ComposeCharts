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

阶段检查记录 41 份 metadata、各 42 份 Native 编译源，编译器插件及工具身份、实际产物和依赖解析；双 ABI 对旧配方的公开 metadata 名称和枚举生成 IR 签名清单进行检查。发布核对 root/leaf GMM、POM scope、完整 sources、原始资料、provenance 和校验和。以下记录是本次独立 producer 与 Pages 的验证结果；公开消费编译及运行结果仍以 consumer 的实际回执为准。

## 本次生产与公开消费状态

[Fork source `bce8769199ad`](https://github.com/qsxs/ComposeCharts/commit/bce8769199ad65d406d0843dd7cc8806c1437b30) 经 [producer 40800613406/1](https://github.com/qsxs/cpf-port-tooling/actions/runs/40800613406/attempts/1) 完成 metadata、OHOS x64、arm64 生产及独立 staging 核验；工作流 B 为 `03ca69673f5875030198588b972a9ff21f35732e`，共享工具锁 A 为 `d0cff88fd1ea2e4f08bb4409f1c802cca0c7f285`。两个 ABI 各核对 604 个 metadata 名称和 4 个枚举的 16 条生成 IR 签名。Runner 物理源码、插件、host compiler、Kotlin/Native 包及原编译输出未保留，本机不能重新哈希这些 runner 文件。

根 GAV 为 `io.github.qsxs.cpf.charts:compose-charts:1.0.0-cpf11-preview-bce8769199ad-40800613406-1`，临时 Maven 根为 `https://qsxs.github.io/cpf-maven/`（[根 POM](https://qsxs.github.io/cpf-maven/io/github/qsxs/cpf/charts/compose-charts/1.0.0-cpf11-preview-bce8769199ad-40800613406-1/compose-charts-1.0.0-cpf11-preview-bce8769199ad-40800613406-1.pom)）。[Pages 制品提交 `65b972781ab9`](https://github.com/qsxs/cpf-maven/commit/65b972781ab9f6dd23c613f6c8a04615236d118d) 的 170 个文件、1,716,733 字节在修正批次中与 staging 匿名核对 170/170 匹配，该批零重试；此前一次本机 POM 请求超时，上一轮统计汇总无效。

Consumer 工作流 B 为 `f06a5b40ce0e6e458ce42c6a9572663502c4ea24`；[40802580898/1](https://github.com/qsxs/cpf-port-tooling/actions/runs/40802580898/attempts/1) 核对 13 个公开对象并解析三配置后，metadata 准备阶段下载 sysroot 连续 10 次连接超时失败；[/2](https://github.com/qsxs/cpf-port-tooling/actions/runs/40802580898/attempts/2) 和 [/3](https://github.com/qsxs/cpf-port-tooling/actions/runs/40802580898/attempts/3) 同样在该阶段超时失败；[/4](https://github.com/qsxs/cpf-port-tooling/actions/runs/40802580898/attempts/4) 使用 Gradle 9.5.0 完成 metadata、OHOS x64 和 arm64 真实编译。该次标准按需下载在 sysroot 出现 Connect timed out 后继续完成，同 runner 重试提案未应用；该次 13 个不同公开 Maven 对象均首次 HTTP 200，大小与 SHA-256 与 staging 对齐；三配置选择正确 root/leaf，sources JAR 的 41 common+1 native 源及 metadata manifest 与 Git 输入匹配，992B ColumnChart/LineChart/PieChart 探针完成三目标编译，双 Native Klib 输入 SHA 对齐。Metadata JAR 的 43 个成员与匿名 HTTP 和 runner trace 记录匹配；转换后 Klib 及 runner 物理输入未上传，本机不能独立重哈其内部。Gradle 8 消费、link、runtime、设备和 Android/iOS 回归未验证，App 未切换。历史饼图 Popup 坐标、预算标签拥挤与提示裁切问题已延期，不能宣称已修复。
