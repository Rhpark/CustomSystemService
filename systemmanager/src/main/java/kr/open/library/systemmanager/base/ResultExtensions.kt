package kr.open.library.systemmanager.base

/**
 * Extension functions for Result type to work with SystemServiceError.
 * SystemServiceError와 함께 작동하는 Result 타입의 확장 함수입니다.
 */

/**
 * Extension function to handle SystemServiceError failures in Result.
 * Result에서 SystemServiceError 실패를 처리하는 확장 함수입니다.
 *
 * @param action The action to execute when the Result contains a SystemServiceError
 * @param action Result에 SystemServiceError가 포함된 경우 실행할 액션
 * @return The original Result for chaining
 * @return 체이닝을 위한 원래 Result
 * 
 * @deprecated Use fold() pattern instead: result.fold(onSuccess = { ... }, onFailure = { error -> when(error) { is SystemServiceException -> ... } })
 * @deprecated fold() 패턴을 사용하세요: result.fold(onSuccess = { ... }, onFailure = { error -> when(error) { is SystemServiceException -> ... } })
 */
@Deprecated(
    message = "Use fold() pattern instead for better functional programming style and consistency",
    replaceWith = ReplaceWith("fold(onSuccess = { /* success handler */ }, onFailure = { error -> when (error) { is SystemServiceException -> action(error.error) } })")
)
inline fun <T> Result<T>.onSystemServiceFailure(action: (error: SystemServiceError) -> Unit): Result<T> {
    if (isFailure) {
        val exception = exceptionOrNull()
        when (exception) {
            is SystemServiceException -> {
                action(exception.error)
            }
            else -> {
                // Convert other exceptions to Unknown.Exception
                // 다른 예외를 Unknown.Exception으로 변환
                val unknownError = SystemServiceError.Unknown.Exception(
                    exception ?: RuntimeException("Unknown error"),
                    "unknown"
                )
                action(unknownError)
            }
        }
    }
    return this
}

/**
 * Extension function similar to onFailure but specifically for SystemServiceException.
 * onFailure와 유사하지만 SystemServiceException을 위한 확장 함수입니다.
 * 
 * @deprecated Use fold() pattern instead: result.fold(onSuccess = { ... }, onFailure = { error -> when(error) { is SystemServiceException -> action(error) } })
 * @deprecated fold() 패턴을 사용하세요: result.fold(onSuccess = { ... }, onFailure = { error -> when(error) { is SystemServiceException -> action(error) } })
 */
@Deprecated(
    message = "Use fold() pattern instead for better functional programming style and consistency",
    replaceWith = ReplaceWith("fold(onSuccess = { /* success handler */ }, onFailure = { error -> when (error) { is SystemServiceException -> action(error) } })")
)
inline fun <T> Result<T>.onSystemServiceException(action: (exception: SystemServiceException) -> Unit): Result<T> {
    if (isFailure) {
        val exception = exceptionOrNull()
        if (exception is SystemServiceException) {
            action(exception)
        }
    }
    return this
}