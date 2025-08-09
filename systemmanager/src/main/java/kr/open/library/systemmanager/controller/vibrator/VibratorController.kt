package kr.open.library.systemmanager.controller.vibrator

import android.Manifest.permission.VIBRATE
import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.extenstions.checkSdkVersion
import kr.open.library.systemmanager.extenstions.getVibrator
import kr.open.library.systemmanager.extenstions.getVibratorManager
import kr.open.library.systemmanager.extenstions.safeCatch

/**
 * Controller for managing device vibration operations with backward compatibility.
 * Handles both legacy Vibrator API (SDK < 31) and modern VibratorManager API (SDK >= 31).
 * 
 * 기기 진동 작업을 관리하는 컨트롤러로 하위 호환성을 제공합니다.
 * 레거시 Vibrator API (SDK < 31)와 최신 VibratorManager API (SDK >= 31)를 모두 처리합니다.
 *
 * Required manifest permission:
 * <uses-permission android:name="android.permission.VIBRATE"/>
 */
public open class VibratorController(context: Context) :
    BaseSystemService(context, listOf(VIBRATE)) {

    /**
     * Legacy vibrator instance for SDK versions below 31 (Android 12).
     * SDK 31 미만 (안드로이드 12 이하) 버전용 레거시 진동 인스턴스입니다.
     */
    public val vibrator: Vibrator by lazy {
        safeCatch("vibrator initialization", context.getVibrator()) {
            checkSdkVersion(
                Build.VERSION_CODES.S,
                positiveWork = {
                    throw IllegalStateException("Can not be used Vibrator Class, required must be lower than SDK version S(31), this SDK version ${Build.VERSION.SDK_INT}")
                },
                negativeWork = { context.getVibrator() }
            )
        }
    }


    /**
     * Modern vibrator manager for SDK 31+ (Android 12+) with enhanced capabilities.
     * SDK 31+ (안드로이드 12+) 버전용 향상된 기능을 제공하는 최신 진동 매니저입니다.
     */
    @get:RequiresApi(Build.VERSION_CODES.S)
    public val vibratorManager: VibratorManager by lazy {
        safeCatch("vibratorManager initialization", context.getVibratorManager()) {
            checkSdkVersion(
                Build.VERSION_CODES.S,
                positiveWork = { context.getVibratorManager() },
                negativeWork = {
                    throw IllegalStateException("Can not be used VibratorManager Class, required SDK version >= Build.VERSION_CODES.S(31), this SDK version ${Build.VERSION.SDK_INT}")
                }
            )
        }
    }

    /**
     * Creates a one-shot vibration with specified duration and amplitude.
     * Supports different APIs based on SDK version for backward compatibility.
     * 
     * 지정된 지속 시간과 강도로 단발성 진동을 생성합니다.
     * 하위 호환성을 위해 SDK 버전에 따라 다른 API를 지원합니다.
     * 
     * @param timer Duration in milliseconds (진동 지속 시간, 밀리초)
     * @param effect Amplitude from -1 to 255, where -1 is default (강도 -1~255, -1은 기본값)
     */
    @RequiresPermission(VIBRATE)
    public fun createOneShot(timer: Long, effect: Int = VibrationEffect.DEFAULT_AMPLITUDE): Boolean {
        return executeWithApiCompatibility(
            operation = "createOneShot",
            supportedApiLevel = Build.VERSION_CODES.Q,
            modernApi = {
                val oneShot = VibrationEffect.createOneShot(timer, effect)
                if (isModernApiSupported(Build.VERSION_CODES.S)) {
                    vibratorManager.vibrate(CombinedVibration.createParallel(oneShot))
                } else {
                    vibrator.vibrate(oneShot)
                }
                true
            },
            legacyApi = {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timer)
                true
            }
        ).getOrDefault(false)
    }
    
    /**
     * 단순 진동 실행 (지속시간만 지정)
     * Simple vibration execution (duration only)
     * 
     * @param milliseconds 진동 지속 시간 (밀리초) / Vibration duration in milliseconds
     */
    @RequiresPermission(VIBRATE)
    public fun vibrate(milliseconds: Long): Boolean {
        return executeWithDeprecatedFallback(
            operation = "vibrate",
            minimumApiLevel = Build.VERSION_CODES.Q,
            modernBlock = {
                val effect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                if (isModernApiSupported(Build.VERSION_CODES.S)) {
                    vibratorManager.vibrate(CombinedVibration.createParallel(effect))
                } else {
                    vibrator.vibrate(effect)
                }
                true
            },
            deprecatedBlock = {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
                true
            }
        ).getOrDefault(false)
    }

    /**
     * Creates a predefined vibration effect using system-defined patterns.
     * Available effects: EFFECT_CLICK, EFFECT_DOUBLE_CLICK, EFFECT_TICK, etc.
     * 
     * 시스템에서 정의된 패턴을 사용하여 미리 정의된 진동 효과를 생성합니다.
     * 사용 가능한 효과: EFFECT_CLICK, EFFECT_DOUBLE_CLICK, EFFECT_TICK 등
     * 
     * @param vibrationEffectClick Predefined effect constant (e.g., VibrationEffect.EFFECT_CLICK)
     */
    @RequiresPermission(VIBRATE)
    public fun createPredefined(vibrationEffectClick: Int): Boolean {
        return executeWithApiCompatibility(
            operation = "createPredefined",
            supportedApiLevel = Build.VERSION_CODES.Q,
            modernApi = {
                val predefinedEffect = VibrationEffect.createPredefined(vibrationEffectClick)
                if (isModernApiSupported(Build.VERSION_CODES.S)) {
                    vibratorManager.vibrate(CombinedVibration.createParallel(predefinedEffect))
                } else {
                    vibrator.vibrate(predefinedEffect)
                }
                true
            },
            legacyApi = {
                // Fallback for older APIs - use simple vibration
                @Suppress("DEPRECATION")
                vibrator.vibrate(100) // Default 100ms for predefined effects
                true
            }
        ).getOrDefault(false)
    }

    /**
     * Creates a complex waveform vibration pattern with custom timing and amplitudes.
     * Supports different APIs based on SDK version for backward compatibility.
     * 
     * 사용자 정의 타이밍과 강도로 복잡한 웨이브폼 진동 패턴을 생성합니다.
     * 하위 호환성을 위해 SDK 버전에 따라 다른 API를 지원합니다.
     *
     * @param times Timing values in milliseconds (타이밍 값, 밀리초 단위)
     * @param amplitudes Amplitude values (0-255) where 0 = motor off (강도 값 0-255, 0은 모터 끔)
     * @param repeat Index to start repeating from, -1 for no repeat (반복 시작 인덱스, -1은 반복 없음)
     */
    @RequiresPermission(VIBRATE)
    public fun createWaveform(times: LongArray, amplitudes: IntArray, repeat: Int = -1): Boolean {
        return executeWithApiCompatibility(
            operation = "createWaveform",
            supportedApiLevel = Build.VERSION_CODES.O,
            modernApi = {
                val waveformEffect = VibrationEffect.createWaveform(times, amplitudes, repeat)
                if (isModernApiSupported(Build.VERSION_CODES.S)) {
                    vibratorManager.vibrate(CombinedVibration.createParallel(waveformEffect))
                } else {
                    vibrator.vibrate(waveformEffect)
                }
                true
            },
            legacyApi = {
                // Fallback for older APIs - use pattern vibration without amplitudes
                @Suppress("DEPRECATION")
                vibrator.vibrate(times, repeat)
                true
            }
        ).getOrDefault(false)
    }
    
    /**
     * 패턴 진동 실행 (타이밍 배열만 사용)
     * Pattern vibration execution (timing array only)
     * 
     * @param pattern 진동 패턴 배열 / Vibration pattern array
     * @param repeat 반복 시작 인덱스, -1은 반복 없음 / Repeat start index, -1 for no repeat
     */
    @RequiresPermission(VIBRATE)
    public fun vibratePattern(pattern: LongArray, repeat: Int = -1): Boolean {
        return executeWithDeprecatedFallback(
            operation = "vibratePattern",
            minimumApiLevel = Build.VERSION_CODES.O,
            modernBlock = {
                val waveformEffect = VibrationEffect.createWaveform(pattern, repeat)
                if (isModernApiSupported(Build.VERSION_CODES.S)) {
                    vibratorManager.vibrate(CombinedVibration.createParallel(waveformEffect))
                } else {
                    vibrator.vibrate(waveformEffect)
                }
                true
            },
            deprecatedBlock = {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, repeat)
                true
            }
        ).getOrDefault(false)
    }

    /**
     * Cancels any ongoing vibration immediately.
     * Works with both legacy Vibrator and modern VibratorManager APIs.
     * 
     * 진행 중인 모든 진동을 즉시 취소합니다.
     * 레거시 Vibrator와 최신 VibratorManager API 모두에서 작동합니다.
     */
    @RequiresPermission(VIBRATE)
    public fun cancel(): Boolean {
        return executeWithApiCompatibility(
            operation = "cancel",
            supportedApiLevel = Build.VERSION_CODES.S,
            modernApi = {
                vibratorManager.cancel()
                true
            },
            legacyApi = {
                vibrator.cancel()
                true
            },
            requiresPermission = true
        ).getOrDefault(false)
    }
    
    /**
     * 진동 지원 여부를 확인합니다.
     * Check if vibration is supported on this device.
     */
    public fun hasVibrator(): Boolean {
        return executeWithApiCompatibility(
            operation = "hasVibrator",
            supportedApiLevel = Build.VERSION_CODES.S,
            modernApi = {
                vibratorManager.defaultVibrator.hasVibrator()
            },
            legacyApi = {
                vibrator.hasVibrator()
            },
            requiresPermission = false
        ).getOrDefault(false)
    }
}