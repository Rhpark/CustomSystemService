package kr.open.library.systemmanager.info.network

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.Manifest.permission.READ_PHONE_STATE
import android.Manifest.permission.READ_PHONE_NUMBERS
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import kr.open.library.systemmanager.base.SystemServiceError
import kr.open.library.systemmanager.base.SystemServiceException

/**
 * NetworkStateInfo의 내부 Result 패턴 기반 구현
 * Internal Result pattern based implementation for NetworkStateInfo
 */
internal object NetworkStateInfoInternal {
    
    /**
     * 안전하게 권한 기반 작업 실행
     * Safely execute permission-based operations
     */
    inline fun <T> safeExecuteWithPermission(
        operation: String,
        requiredPermissions: List<String>,
        crossinline block: () -> T
    ): Result<T> {
        return try {
            val result = block()
            Result.success(result)
        } catch (e: SecurityException) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Permission.NotGranted(requiredPermissions),
                    e
                )
            )
        } catch (e: IllegalStateException) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.State.InvalidState(
                        currentState = "unknown",
                        requiredState = "initialized", 
                        operation = operation
                    ),
                    e
                )
            )
        } catch (e: NoSuchMethodError) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.SystemService.UnsupportedVersion(
                        serviceName = operation,
                        requiredApi = -1,
                        currentApi = android.os.Build.VERSION.SDK_INT
                    ),
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Unknown.Exception(e, operation),
                    e
                )
            )
        }
    }
    
    /**
     * 안전하게 텔레포니 작업 실행  
     * Safely execute telephony operations
     */
    inline fun <T> safeExecuteTelephonyOperation(
        operation: String,
        simSlotIndex: Int? = null,
        crossinline block: () -> T
    ): Result<T> {
        return try {
            val result = block()
            Result.success(result)
        } catch (e: SecurityException) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Permission.NotGranted(listOf(READ_PHONE_STATE)),
                    e
                )
            )
        } catch (e: IllegalStateException) {
            val errorMsg = simSlotIndex?.let { "TelephonyManager[$it] is null" } ?: "Invalid telephony state"
            Result.failure(
                SystemServiceException(
                    SystemServiceError.State.InvalidState(
                        currentState = "uninitialized",
                        requiredState = "initialized",
                        operation = "$operation${simSlotIndex?.let { "[$it]" } ?: ""}"
                    ),
                    e
                )
            )
        } catch (e: IllegalArgumentException) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Configuration.InvalidParameter(
                        parameterName = "callback or telephonyManager",
                        value = simSlotIndex,
                        reason = e.message ?: "Invalid callback provided"
                    ),
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Unknown.Exception(e, "$operation${simSlotIndex?.let { "[$it]" } ?: ""}"),
                    e
                )
            )
        }
    }
    
    /**
     * 안전하게 네트워크 작업 실행
     * Safely execute network operations  
     */
    inline fun <T> safeExecuteNetworkOperation(
        operation: String,
        crossinline block: () -> T
    ): Result<T> {
        return try {
            val result = block()
            Result.success(result)
        } catch (e: SecurityException) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Permission.NotGranted(listOf(ACCESS_NETWORK_STATE)),
                    e
                )
            )
        } catch (e: IllegalStateException) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.State.InvalidState(
                        currentState = "disconnected",
                        requiredState = "connected",
                        operation = operation
                    ),
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Network.OperationFailed(operation, e),
                    e
                )
            )
        }
    }
    
    /**
     * Null 체크와 함께 Result 생성
     * Create Result with null check
     */
    fun <T : Any> createResultWithNullCheck(
        value: T?,
        errorMessage: String,
        operation: String
    ): Result<T?> {
        return try {
            Result.success(value)
        } catch (e: Exception) {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Resource.NotFound("data", operation),
                    e
                )
            )
        }
    }
    
    /**
     * 하드웨어 기능 지원 확인
     * Check hardware feature support
     */
    fun checkHardwareSupport(featureName: String, isSupported: Boolean): Result<Boolean> {
        return if (isSupported) {
            Result.success(true)
        } else {
            Result.failure(
                SystemServiceException(
                    SystemServiceError.Hardware.FeatureNotSupported(featureName)
                )
            )
        }
    }
}