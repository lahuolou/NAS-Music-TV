package com.lnh.nasmusictv.ui.components

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import com.lnh.nasmusictv.ui.theme.PlayerVisualSpec

/**
 * 封面**预取**（2026-10-30 用户反馈「封面加载过慢」）。
 *
 * 问题：封面只在切歌那一刻才由 [coil.compose.AsyncImage] 发起网络请求，
 * 慢网/大图时盘心先空一两秒（黑胶窗口里只有占位音符），换碟转场"滑进来的是占位图"。
 *
 * 方案：当前歌曲确立时，把「当前 / 下一曲 / 上一曲」的封面**提前**丢给 Coil。
 * 图像字节落进 Coil 的磁盘缓存（[com.lnh.nasmusictv.NasMusicApp.newImageLoader]
 * 已配 100MB 磁盘 + 10% 内存），真正切过去时 AsyncImage 直接本地命中，
 * 不再走网络；背景取色（[com.lnh.nasmusictv.ui.theme.CoverPaletteExtractor]，
 * 同样经 Coil 解码 96px）也因此从磁盘缓存取字节，切歌背景色几乎同步就绪。
 *
 * 设计要点：
 * - 只 enqueue、不 await —— 不阻塞调用方，也不占用主线程（Coil 自带并发控制）；
 * - 同一 URL 已在缓存时 Coil 直接返回，不重复下载（重复调用零成本）；
 * - 请求尺寸按「黑胶盘心封面」实际像素估算（屏高 × 盘径占比 × 封面占比），
 *   避免为预取解码全尺寸原图；与视图请求尺寸不完全一致时仍能命中磁盘缓存。
 */
object CoverPrefetcher {

    /** 预取解码边长上限（px）：盘心封面在 1440p 上约 480px，再大无意义 */
    private const val MAX_PREFETCH_PX = 768
    private const val MIN_PREFETCH_PX = 256
    /** 当前/前后曲及各自候选封面总数上限，避免异常后端返回大量候选造成预取风暴。 */
    private const val MAX_PREFETCH_URLS = 12

    private val requestLock = Any()
    private val activeRequests = LinkedHashMap<String, Disposable>()
    private var requestGeneration = 0L

    /**
     * 把若干封面 URL 预热进 Coil 缓存。重复 URL 自动去重；空串/空白跳过。
     * 线程安全：可在任意调度器调用（enqueue 只入队）。
     */
    fun prefetch(context: Context, urls: List<String>, sizePx: Int = defaultSizePx(context)) {
        val selectedUrls = urls.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PREFETCH_URLS)
            .toList()

        // 切歌/重排队列时取消上一代仍在下载的预取；否则快速连按下一曲会让过期封面
        // 继续占用连接、解码线程与缓存。已完成并进入 Coil 缓存的结果不受影响。
        val generation = synchronized(requestLock) {
            requestGeneration += 1L
            activeRequests.values.forEach { disposable -> runCatching { disposable.dispose() } }
            activeRequests.clear()
            requestGeneration
        }
        if (selectedUrls.isEmpty()) return

        val loader = context.imageLoader
        val targetPx = sizePx.coerceIn(MIN_PREFETCH_PX, MAX_PREFETCH_PX)
        selectedUrls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(targetPx)
                // 预取的目的就是填缓存：三级缓存都显式打开（调用方若关过也覆盖回来）
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                // 预取不画图：允许硬件位图（省内存、解码更快）
                .allowHardware(true)
                .build()
            val disposable = runCatching { loader.enqueue(request) }.getOrNull() ?: return@forEach
            synchronized(requestLock) {
                if (generation == requestGeneration) {
                    activeRequests[url] = disposable
                } else {
                    // 新一代切歌请求已经先行注册：本次旧请求不得继续后台占用资源。
                    runCatching { disposable.dispose() }
                }
            }
        }
    }

    /** AppRoot 离开组合时释放尚未完成的预取任务。 */
    fun cancelPending() {
        synchronized(requestLock) {
            requestGeneration += 1L
            activeRequests.values.forEach { disposable -> runCatching { disposable.dispose() } }
            activeRequests.clear()
        }
    }

    /** 黑胶盘心封面在本次设备上的大致像素边长（可视态：屏高 × 盘径占比 × 封面占比） */
    private fun defaultSizePx(context: Context): Int {
        val screenH = context.resources.displayMetrics.heightPixels
        return (screenH * PlayerVisualSpec.DISC_HEIGHT_FRACTION * PlayerVisualSpec.DISC_COVER_FRACTION)
            .toInt()
    }
}
