package kr.open.library.systemmanager.info.battery

/**
 * Sealed class representing various battery state events.
 * Each event contains specific battery information that can be observed.
 */
public sealed class BatteryStateEvent {
    /**
     * Event for battery capacity updates.
     * @param percent Battery remaining capacity as percentage (0-100)
     */
    public data class OnCapacity(val percent: Int) : BatteryStateEvent()

    /**
     * Event for battery charge counter updates.
     * @param counter Battery charge counter in microampere-hours (µAh)
     */
    public data class OnChargeCounter(val counter: Int) : BatteryStateEvent()

    /**
     * Event for power source connection type updates.
     * @param type Power source type as defined in [android.os.BatteryManager]
     * @see android.os.BatteryManager.BATTERY_PLUGGED_USB
     * @see android.os.BatteryManager.BATTERY_PLUGGED_AC
     * @see android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS
     */
    public data class OnChargePlug(val type: Int) : BatteryStateEvent()

    /**
     * Event for battery temperature updates.
     * @param temperature Battery temperature in Celsius
     */
    public data class OnTemperature(val temperature: Double) : BatteryStateEvent()

    /**
     * Event for battery voltage updates.
     * @param voltage Battery voltage in Volts
     */
    public data class OnVoltage(val voltage: Double) : BatteryStateEvent()

    /**
     * Event for instantaneous battery current updates.
     * @param current Current in microamperes (µA), positive when charging, negative when discharging
     */
    public data class OnCurrentAmpere(val current: Int) : BatteryStateEvent()

    /**
     * Event for average battery current updates.
     * @param current Average current in microamperes (µA), positive when charging, negative when discharging
     */
    public data class OnCurrentAverageAmpere(val current: Int) : BatteryStateEvent()

    /**
     * Event for battery charge status updates.
     * @param status Charge status as defined in [android.os.BatteryManager]
     * @see android.os.BatteryManager.BATTERY_STATUS_CHARGING
     * @see android.os.BatteryManager.BATTERY_STATUS_DISCHARGING
     * @see android.os.BatteryManager.BATTERY_STATUS_FULL
     * @see android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING
     */
    public data class OnChargeStatus(val status: Int) : BatteryStateEvent()

    /**
     * Event for battery health updates.
     * @param health Health status as defined in [android.os.BatteryManager]
     * @see android.os.BatteryManager.BATTERY_HEALTH_GOOD
     * @see android.os.BatteryManager.BATTERY_HEALTH_COLD
     * @see android.os.BatteryManager.BATTERY_HEALTH_DEAD
     * @see android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE
     */
    public data class OnHealth(val health: Int) : BatteryStateEvent()

    /**
     * Event for battery energy counter updates.
     * @param energy Energy in nanowatt-hours (nWh)
     */
    public data class OnEnergyCounter(val energy: Long) : BatteryStateEvent()

    /**
     * Event for battery presence status updates.
     * @param present True if battery is present, false otherwise
     */
    public data class OnPresent(val present: Boolean) : BatteryStateEvent()

    /**
     * Event for battery total capacity updates.
     * @param totalCapacity Total battery capacity in milliampere-hours (mAh)
     */
    public data class OnTotalCapacity(val totalCapacity: Double) : BatteryStateEvent()
}