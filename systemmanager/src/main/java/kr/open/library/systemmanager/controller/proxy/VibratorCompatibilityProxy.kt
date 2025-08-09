package kr.open.library.systemmanager.controller.proxy

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.extenstions.getVibrator
import kr.open.library.systemmanager.extenstions.getVibratorManager

/**
 * 진동 관련 deprecated API들의 호환성 프록시
 * Compatibility proxy for vibrator-related deprecated APIs
 */
public class VibratorCompatibilityProxy(context: Context) : BaseCompatibilityProxy(
    context,
    Build.VERSION_CODES.S // API 31+에서 VibratorManager 도입
) {
    
    private val legacyVibrator: Vibrator by lazy { context.getVibrator() }
    
    @get:RequiresApi(Build.VERSION_CODES.S)
    private val modernVibratorManager: VibratorManager by lazy { context.getVibratorManager() }
    
    /**
     * 단순 진동 실행 프록시 (지속시간만 지정)
     * Simple vibration execution proxy (duration only)
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public fun vibrate(milliseconds: Long): Boolean = executeSafely(
        operation = "VibratorProxy.vibrate",
        defaultValue = false,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: VibrationEffect 사용
                val effect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    modernVibratorManager.vibrate(CombinedVibration.createParallel(effect))
                } else {
                    legacyVibrator.vibrate(effect)
                }
            } else {
                // API 28 이하: deprecated vibrate(long) 사용
                @Suppress("DEPRECATION")
                legacyVibrator.vibrate(milliseconds)
            }
            true
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            legacyVibrator.vibrate(milliseconds)
            true
        }
    )
    
    /**
     * 패턴 진동 실행 프록시
     * Pattern vibration execution proxy
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public fun vibratePattern(pattern: LongArray, repeat: Int = -1): Boolean = executeSafely(
        operation = "VibratorProxy.vibratePattern",
        defaultValue = false,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: VibrationEffect 사용
                val effect = VibrationEffect.createWaveform(pattern, repeat)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    modernVibratorManager.vibrate(CombinedVibration.createParallel(effect))
                } else {
                    legacyVibrator.vibrate(effect)
                }
            } else {
                // API 28 이하: deprecated vibrate(pattern, repeat) 사용
                @Suppress("DEPRECATION")
                legacyVibrator.vibrate(pattern, repeat)
            }
            true
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            legacyVibrator.vibrate(pattern, repeat)
            true
        }
    )
    
    /**
     * 강도가 있는 단발성 진동 프록시
     * One-shot vibration with amplitude proxy
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public fun createOneShot(duration: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE): Boolean = executeSafely(
        operation = "VibratorProxy.createOneShot",
        defaultValue = false,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createOneShot(duration, amplitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    modernVibratorManager.vibrate(CombinedVibration.createParallel(effect))
                } else {
                    legacyVibrator.vibrate(effect)
                }
            } else {
                // API 28 이하: 강도 설정 불가, 단순 진동만
                @Suppress("DEPRECATION")
                legacyVibrator.vibrate(duration)
            }
            true
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            legacyVibrator.vibrate(duration)
            true
        }
    )
    
    /**
     * 미리 정의된 효과 진동 프록시
     * Predefined effect vibration proxy
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public fun createPredefined(effectId: Int): Boolean = executeSafely(
        operation = "VibratorProxy.createPredefined",
        defaultValue = false,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createPredefined(effectId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    modernVibratorManager.vibrate(CombinedVibration.createParallel(effect))
                } else {
                    legacyVibrator.vibrate(effect)
                }
            } else {
                // API 28 이하: 미리 정의된 효과 지원 안함, 기본 진동으로 대체
                Logx.w("Predefined effects not supported on API ${Build.VERSION.SDK_INT}. Using default vibration.")
                @Suppress("DEPRECATION")
                legacyVibrator.vibrate(200) // 200ms 기본 진동
            }
            true
        },
        legacyApi = {
            // API 29 미만: 미리 정의된 효과 지원 안함
            Logx.w("Predefined effects not supported on API ${Build.VERSION.SDK_INT}. Using default vibration.")
            @Suppress("DEPRECATION")
            legacyVibrator.vibrate(200)
            true
        }
    )
    
    /**
     * 웨이브폼 진동 프록시 (강도 배열 포함)
     * Waveform vibration proxy (with amplitude array)
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public fun createWaveform(timings: LongArray, amplitudes: IntArray, repeat: Int = -1): Boolean = executeSafely(
        operation = "VibratorProxy.createWaveform",
        defaultValue = false,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, repeat)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    modernVibratorManager.vibrate(CombinedVibration.createParallel(effect))
                } else {
                    legacyVibrator.vibrate(effect)
                }
            } else {
                // API 28 이하: 강도 배열 지원 안함, 타이밍만 사용
                Logx.w("Amplitude array not supported on API ${Build.VERSION.SDK_INT}. Using timing only.")
                @Suppress("DEPRECATION")
                legacyVibrator.vibrate(timings, repeat)
            }
            true
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            legacyVibrator.vibrate(timings, repeat)
            true
        }
    )
    
    /**
     * 진동 취소 프록시
     * Vibration cancellation proxy
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public fun cancel(): Boolean = executeSafely(
        operation = "VibratorProxy.cancel",
        defaultValue = false,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernVibratorManager.cancel()
            } else {
                legacyVibrator.cancel()
            }
            true
        },
        legacyApi = {
            legacyVibrator.cancel()
            true
        }
    )
    
    /**
     * 진동 지원 여부 확인 프록시
     * Vibration support check proxy
     */
    public fun hasVibrator(): Boolean = executeSafely(
        operation = "VibratorProxy.hasVibrator",
        defaultValue = false,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernVibratorManager.defaultVibrator.hasVibrator()
            } else {
                legacyVibrator.hasVibrator()
            }
        },
        legacyApi = {
            legacyVibrator.hasVibrator()
        }
    )
}