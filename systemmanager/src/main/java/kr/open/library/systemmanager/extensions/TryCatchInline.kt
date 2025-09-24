package kr.open.library.systemmanager.extensions

import kotlinx.coroutines.CancellationException
import kr.open.library.logcat.Logx


public inline fun <T> safeCatch(
    operation: String,
    defaultValue: T,
    block: () -> T
): T {
    return try {
        block()
    } catch (e: CancellationException) { // 코루틴 취소는 반드시 전파
        throw e
    } catch (e: Error) { // OOM 등은 절대 삼키지 않음
        throw e
    } catch (e: Exception) {
        Logx.w("$operation: Operation failed - ${e.message}")
        defaultValue
    }
}