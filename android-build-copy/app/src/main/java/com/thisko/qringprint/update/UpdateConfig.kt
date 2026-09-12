package com.thisko.qringprint.update

/**
 * 更新服务配置.
 * 实际生产时把 [manifestUrl] 换成你的云端 update.json 访问地址.
 *  - GitHub + jsDelivr 方案: https://cdn.jsdelivr.net/gh/<user>/<repo>@latest/update.json
 *  - Cloudflare R2 方案:     https://pub-xxx.r2.dev/<bucket>/update.json
 *  - 自建服务器:             https://your-domain.com/update.json
 */
object UpdateConfig {
    // ---- ↓↓↓ 改成你自己的 manifestUrl ↓↓↓ ----
    // 格式: https://cdn.jsdelivr.net/gh/<GitHub用户名>/<仓库名>@latest/update.json
    // 例子: https://cdn.jsdelivr.net/gh/zhangsan/huanxongkuaiyin-updates@latest/update.json
    const val manifestUrl: String =
        "https://cdn.jsdelivr.net/gh/Xia8250/huanxongkuaiyin-updates@main/update.json"
    // ---- ↑↑↑ 改成你自己的 manifestUrl ↑↑↑ ----

    const val networkTimeoutMs: Long = 15_000L
}
