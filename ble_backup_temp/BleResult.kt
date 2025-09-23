package kr.open.library.systemmanager.controller.bluetooth.base

import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError

/**
 * BLE 전용 Result 패턴
 * BLE-specific Result pattern
 * 
 * BLE 작업의 성공/실패를 안전하게 처리하기 위한 전용 Result 타입입니다.
 * Dedicated Result type for safely handling BLE operation success/failure.
 */
sealed class Result<out T> {
    /**
     * 성공 결과
     * Success result
     */
    data class Success<out T>(val value: T) : Result<T>()
    
    /**
     * 실패 결과
     * Failure result
     */
    data class Failure<out T>(val error: BleServiceError) : Result<T>()
    
    companion object {
        /**
         * 성공 결과 생성
         * Create success result
         */
        fun <T> success(value: T): Result<T> = Success(value)
        
        /**
         * 실패 결과 생성
         * Create failure result
         */
        fun <T> failure(error: BleServiceError): Result<T> = Failure(error)
    }
}

/**
 * Result 확장 함수들
 * Result extension functions
 */

/**
 * 성공인지 확인
 * Check if success
 */
fun <T> Result<T>.isSuccess(): Boolean = this is Result.Success

/**
 * 실패인지 확인
 * Check if failure
 */
fun <T> Result<T>.isFailure(): Boolean = this is Result.Failure

/**
 * 성공 값 가져오기 (실패시 null)
 * Get success value (null if failure)
 */
fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> value
    is Result.Failure -> null
}

/**
 * 실패 오류 가져오기 (성공시 null)
 * Get failure error (null if success)
 */
fun <T> Result<T>.errorOrNull(): BleServiceError? = when (this) {
    is Result.Success -> null
    is Result.Failure -> error
}

/**
 * 성공시 값 반환, 실패시 기본값 반환
 * Return value on success, default value on failure
 */
fun <T> Result<T>.getOrDefault(defaultValue: T): T = when (this) {
    is Result.Success -> value
    is Result.Failure -> defaultValue
}

/**
 * 성공시 변환 함수 적용
 * Apply transform function on success
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(value))
    is Result.Failure -> Result.Failure(error)
}

/**
 * 실패시 오류 변환
 * Transform error on failure
 */
inline fun <T> Result<T>.mapError(transform: (BleServiceError) -> BleServiceError): Result<T> = when (this) {
    is Result.Success -> this
    is Result.Failure -> Result.Failure(transform(error))
}