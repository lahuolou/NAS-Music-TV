package com.lnh.nasmusictv.util

import android.content.Context
import android.widget.Toast

/**
 * 统一的应用内轻提示（Toast）出口。
 *
 * 收敛此前散落在各处的 `Toast.makeText(...).show()` 调用：
 * - 统一用 ApplicationContext，避免短生命周期 Context（如 Activity）引发泄漏 / 窗口越界；
 * - 调用点语义统一为 `AppToast.show(ctx, text)` / `AppToast.show(ctx, resId)`；
 * - 行为与原生 `Toast.makeText(...).show()` 完全一致（每次调用即弹，不去重），
 *   以保证「同一结果消息需重复提示」的既有契约不被破坏。
 */
object AppToast {
    fun show(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context.applicationContext, text, duration).show()
    }

    fun show(context: Context, resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        show(context, context.applicationContext.getString(resId), duration)
    }
}
