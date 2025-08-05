package kr.open.library.systemmanager.base

import android.content.Context
import android.os.Build
import kr.open.library.logcat.Logx
import kr.open.library.permissions.extensions.remainPermissions

/**
 * Base class for system services with integrated Result pattern support.
 * Result 패턴 지원이 통합된 시스템 서비스의 기본 클래스입니다.
 *
 * This class provides:
 * 이 클래스는 다음을 제공합니다:
 * - Permission management / 권한 관리
 * - Common error handling with Result pattern / Result 패턴을 통한 공통 오류 처리
 * - Standardized error categorization / 표준화된 오류 분류
 * - Utility methods for safe operations / 안전한 작업을 위한 유틸리티 메서드
 *
 * @param context The application context.
 * @param context 애플리케이션 컨텍스트.
 * @param requiredPermissions The list of required permissions.
 * @param requiredPermissions 필요한 권한 목록입니다.
 */
public abstract class BaseSystemService(
    protected val context: Context, 
    private val requiredPermissions: List<String>? = null
) {

    private var remainPermissions = emptyList<String>()

    init {
        requiredPermissions?.let {
            remainPermissions = context.remainPermissions(it)
            if(remainPermissions.isEmpty()) {
                Logx.d("All required permissions granted for ${this::class.simpleName}")
            } else {
                Logx.w("Missing permissions for ${this::class.simpleName}: $remainPermissions")
            }
        }
    }

    /**
     * Gets the list of denied permissions.
     * 거부된 권한 목록을 가져옵니다.
     */
    protected fun getDeniedPermissionList(): List<String> = remainPermissions

    /**
     * Checks if all required permissions have been granted.
     * 모든 필수 권한이 부여되었는지 확인합니다.
     *
     * @return Returns true if all permissions have been granted, false otherwise.
     * @return 모든 권한이 부여된 경우 true, 그렇지 않으면 false를 반환.
     */
    protected fun isPermissionAllGranted(): Boolean = remainPermissions.isEmpty()

    /**
     * Creates a permission error for missing permissions.
     * 누락된 권한에 대한 권한 오류를 생성합니다.
     */
    protected fun createPermissionError(): SystemServiceError.Permission.NotGranted {
        return SystemServiceError.Permission.NotGranted(remainPermissions)
    }

    /**
     * Executes a block of code safely with Result pattern, handling common system service errors.
     * Result 패턴을 사용하여 일반적인 시스템 서비스 오류를 처리하며 코드 블록을 안전하게 실행합니다.
     *
     * @param operation Name of the operation for logging
     * @param operation 로깅을 위한 작업 이름
     * @param requiresPermission Whether this operation requires permissions to be granted
     * @param requiresPermission 이 작업에 권한이 필요한지 여부
     * @param block The operation to execute
     * @param block 실행할 작업
     * @return Result containing success value or SystemServiceError
     */
    protected inline fun <T> safeExecute(
        operation: String,
        requiresPermission: Boolean = true,
        block: () -> T
    ): Result<T> {
        return runCatching {
            // Check permissions if required
            if (requiresPermission && !isPermissionAllGranted()) {
                throw SystemServiceException(createPermissionError())
            }
            
            try {
                val result = block()
                Logx.d("${this::class.simpleName}.$operation: Success")
                result
            } catch (e: SecurityException) {
                val error = handleSecurityException(e, operation)
                Logx.e("${this::class.simpleName}.$operation: Security error - ${error.getDeveloperMessage()}")
                throw SystemServiceException(error, e)
            } catch (e: IllegalArgumentException) {
                val error = SystemServiceError.Configuration.InvalidParameter(
                    parameterName = "unknown",
                    value = null,
                    reason = e.message ?: "Invalid argument"
                )
                Logx.e("${this::class.simpleName}.$operation: Invalid argument - ${e.message}")
                throw SystemServiceException(error, e)
            } catch (e: IllegalStateException) {
                val error = SystemServiceError.State.InvalidState(
                    currentState = "unknown",
                    requiredState = "valid",
                    operation = operation
                )
                Logx.e("${this::class.simpleName}.$operation: Invalid state - ${e.message}")
                throw SystemServiceException(error, e)
            } catch (e: UnsupportedOperationException) {
                val error = SystemServiceError.SystemService.UnsupportedVersion(
                    serviceName = this::class.simpleName ?: "unknown",
                    requiredApi = 0,
                    currentApi = Build.VERSION.SDK_INT
                )
                Logx.e("${this::class.simpleName}.$operation: Unsupported operation - ${e.message}")
                throw SystemServiceException(error, e)
            } catch (e: Exception) {
                val error = SystemServiceError.Unknown.Exception(e, operation)
                Logx.e("${this::class.simpleName}.$operation: Unexpected error - ${e.message}")
                throw SystemServiceException(error, e)
            }
        }
    }

    /**
     * Handles SecurityException and categorizes it appropriately.
     * SecurityException을 처리하고 적절하게 분류합니다.
     */
    private fun handleSecurityException(e: SecurityException, operation: String): SystemServiceError {
        val message = e.message ?: ""
        
        return when {
            // Permission-related security errors
            message.contains("permission", ignoreCase = true) -> {
                when {
                    message.contains("SYSTEM_ALERT_WINDOW") -> 
                        SystemServiceError.Permission.SpecialPermissionRequired(
                            "SYSTEM_ALERT_WINDOW",
                            "android.settings.action.MANAGE_OVERLAY_PERMISSION"
                        )
                    message.contains("SCHEDULE_EXACT_ALARM") ->
                        SystemServiceError.Permission.SpecialPermissionRequired(
                            "SCHEDULE_EXACT_ALARM",
                            "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
                        )
                    else -> SystemServiceError.Permission.NotGranted(remainPermissions.ifEmpty { listOf("unknown") })
                }
            }
            
            // Policy-related security errors
            message.contains("policy", ignoreCase = true) || message.contains("admin", ignoreCase = true) -> {
                SystemServiceError.Security.PolicyViolation("device_policy", operation)
            }
            
            // Access denied
            message.contains("denied", ignoreCase = true) || message.contains("forbidden", ignoreCase = true) -> {
                SystemServiceError.Security.AccessDenied(operation, message)
            }
            
            // Default security error
            else -> SystemServiceError.Security.PolicyViolation("security_policy", operation)
        }
    }

    /**
     * Executes an operation that returns boolean and converts it to Result<Unit>.
     * boolean을 반환하는 작업을 실행하고 Result<Unit>로 변환합니다.
     */
    protected inline fun safeExecuteBoolean(
        operation: String,
        requiresPermission: Boolean = true,
        block: () -> Boolean
    ): Result<Unit> {
        return safeExecute(operation, requiresPermission) {
            val success = block()
            if (success) {
                Unit
            } else {
                throw IllegalStateException("Operation '$operation' returned false")
            }
        }
    }

    /**
     * Executes an operation with a default fallback value.
     * 기본 대체 값과 함께 작업을 실행합니다.
     */
    protected inline fun <T> safeExecuteOrDefault(
        operation: String,
        defaultValue: T,
        requiresPermission: Boolean = true,
        block: () -> T
    ): T {
        return safeExecute(operation, requiresPermission, block).getOrDefault(defaultValue)
    }

    /**
     * Refreshes the permission status. Call this after requesting permissions.
     * 권한 상태를 새로고침합니다. 권한 요청 후 이를 호출하세요.
     */
    public fun refreshPermissions() {
        requiredPermissions?.let {
            remainPermissions = context.remainPermissions(it)
            if (remainPermissions.isEmpty()) {
                Logx.d("All permissions granted after refresh for ${this::class.simpleName}")
            }
        }
    }

    /**
     * Gets information about required permissions and their status.
     * 필요한 권한과 그 상태에 대한 정보를 가져옵니다.
     */
    public fun getPermissionInfo(): Map<String, Boolean> {
        return requiredPermissions?.associateWith { permission ->
            !remainPermissions.contains(permission)
        } ?: emptyMap()
    }

    /**
     * Checks if a specific permission is granted.
     * 특정 권한이 부여되었는지 확인합니다.
     */
    protected fun isPermissionGranted(permission: String): Boolean {
        return !remainPermissions.contains(permission)
    }

    /**
     * Called when the service is being destroyed. Override to perform cleanup.
     * 서비스가 소멸될 때 호출됩니다. 정리 작업을 수행하려면 재정의하세요.
     */
    public open fun onDestroy() {
        Logx.d("${this::class.simpleName} destroyed")
    }
}