package kr.open.library.systemmanager.extenstions

import kr.open.library.logcat.Logx


public inline fun <T> safeCatch(
    operation: String,
    defaultValue: T,
    block: () -> T
): T {
    return try {
        block()
    } catch (e: Exception) {
        e.printStackTrace()
        Logx.e("$operation: ${e.message}")
        defaultValue
    }
}