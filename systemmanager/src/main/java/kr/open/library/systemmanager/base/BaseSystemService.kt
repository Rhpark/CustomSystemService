package kr.open.library.systemmanager.base

import android.content.Context
import kr.open.library.logcat.Logx
import kr.open.library.permissions.extensions.remainPermissions

/**
 * Base class for system services.
 * 시스템 서비스에 대한 기본 클래스.
 *
 *
 * @param context The application context.
 * @param context 애플리케이션 컨텍스트.
 *
 * @param requiredPermissions The list of required permissions.
 * @param requiredPermissions 필요한 권한 목록입니다.
 */
public abstract class BaseSystemService(protected val context: Context, requiredPermissions: List<String>? = null) {

    private var remainPermissions = emptyList<String>()

    init {
        requiredPermissions?.let {
            remainPermissions = context.remainPermissions(it)
            if(remainPermissions.isEmpty()) {
                Logx.d("Requires that permission $remainPermissions")
            }
        }
    }


    protected fun getDeniedPermissionList(): List<String> = remainPermissions

    /**
     * Checks if all permissions have been granted.
     * 모든 권한이 부여되었는지 확인.
     *
     * @return Returns true if all permissions have been granted, false otherwise.
     * @return 모든 권한이 부여된 경우 true, 그렇지 않으면 false를 반환.
     */
    protected fun isPermissionAllGranted() :Boolean = remainPermissions.isEmpty()

    public open fun onDestroy() {}
}