package kr.open.library.systemmanager.info.battery.power

import android.content.Context
import kr.open.library.logcat.Logx
import java.lang.reflect.Method

/**
 * PowerProfile
 *
 * This class provides access to device power consumption information through the internal
 * Android PowerProfile class using reflection.
 *
 * The primary use case is to retrieve the battery's total capacity (rated capacity),
 * but it can also be used to get other power consumption metrics defined in [PowerProfileVO].
 *
 * Note: Because this uses reflection to access internal Android APIs, it may not work
 * on all devices or future Android versions.
 *
 * @see PowerProfileVO for available power metrics
 */
public class PowerProfile(context: Context) {

    private val classNamePowerProfile: String = "com.android.internal.os.PowerProfile"
    private val averagePower: String = "getAveragePower"

    private val powerProfileClass: Class<*>
    private val getAveragePowerMethod: Method
    private val getAveragePowerMethodWithInt: Method
    private val powerProfileInstance: Any

    init {
        try {
            powerProfileClass = Class.forName(classNamePowerProfile)

            // Get method for retrieving average power by string key
            getAveragePowerMethod = powerProfileClass.getMethod(averagePower, String::class.java)

            // Get method for retrieving average power by string key and index
            getAveragePowerMethodWithInt = powerProfileClass.getMethod(
                averagePower, String::class.java, Int::class.javaPrimitiveType
            )

            // Create an instance of PowerProfile
            powerProfileInstance =
                powerProfileClass.getConstructor(Context::class.java).newInstance(context)
        } catch (e: Exception) {
            Logx.e("Failed to initialize PowerProfile: ${e.message}")
            throw RuntimeException("Failed to initialize PowerProfile", e)
        }
    }

    /**
     * Retrieves the average power consumption for the specified power profile type.
     *
     * @param type The power profile type to retrieve from [PowerProfileVO]
     * @return The average power consumption as a Double, or 0.0 if an error occurred
     */
    public fun getAveragePower(type: PowerProfileVO): Any? {
        return try {
            getAveragePowerMethod.invoke(powerProfileInstance, type.res)
        } catch (e: Exception) {
            Logx.e("Error getting average power for ${type.res}: ${e.message}")
            null
        }
    }

    /**
     * Retrieves the average power consumption for the specified power profile type and index.
     * This is used for metrics that have multiple values (like CPU clusters).
     *
     * @param type The power profile type to retrieve from [PowerProfileVO]
     * @param index The index of the power profile value
     * @return The average power consumption as a Double, or 0.0 if an error occurred
     */
    public fun getAveragePower(type: PowerProfileVO, index: Int): Any? {
        return try {
            getAveragePowerMethodWithInt.invoke(powerProfileInstance, type.res, index)
        } catch (e: Exception) {
            Logx.e("Error getting average power for ${type.res}[$index]: ${e.message}")
            null
        }
    }

    /**
     * Gets the total battery capacity in milliampere-hours (mAh).
     *
     * @return The battery capacity in mAh, or 0.0 if unable to retrieve
     */
    public fun getBatteryCapacity(): Double {
        return getAveragePower(PowerProfileVO.POWER_BATTERY_CAPACITY) as? Double ?: 0.0
    }
}