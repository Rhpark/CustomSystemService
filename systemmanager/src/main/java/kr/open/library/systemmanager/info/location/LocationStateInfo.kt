package kr.open.library.systemmanager.info.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.open.library.logcat.Logx
import kr.open.library.permissions.extensions.hasPermissions
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.base.DataUpdate
import kr.open.library.systemmanager.extensions.checkSdkVersion
import kr.open.library.systemmanager.extensions.getLocationManager
import kr.open.library.systemmanager.base.SystemServiceError
import kr.open.library.systemmanager.base.SystemServiceException

public open class LocationStateInfo(
    context: Context,
    private val coroutineScope: CoroutineScope
) :
    BaseSystemService(context, listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)) {

    public val locationManager: LocationManager by lazy { context.getLocationManager() }

    private val msfUpdate: MutableStateFlow<LocationStateEvent> = MutableStateFlow(LocationStateEvent.OnGpsEnabled(isGpsEnabled()))
    public val sfUpdate: StateFlow<LocationStateEvent> = msfUpdate.asStateFlow()

    private val locationChanged = DataUpdate<Location?>(getLocation())
    private val isGpsEnabled = DataUpdate<Boolean>(isGpsEnabled())
    private val isNetworkEnabled = DataUpdate<Boolean>(isNetworkEnabled())
    private val isPassiveEnabled = DataUpdate<Boolean>(isPassiveEnabled())
    private val isFusedEnabled = DataUpdate<Boolean>(checkSdkVersion(Build.VERSION_CODES.S, positiveWork = {isFusedEnabled()}, negativeWork = {false}))

    init {
        setupDataFlows()
    }

    /**
     * Sets up reactive flows for all location data updates
     */
    private fun setupDataFlows() {
        coroutineScope.launch {
            locationChanged.state.collect { sendFlow(LocationStateEvent.OnLocationChanged(it)) }
        }
        coroutineScope.launch {
            isGpsEnabled.state.collect { sendFlow(LocationStateEvent.OnGpsEnabled(it)) }
        }
        coroutineScope.launch {
            isNetworkEnabled.state.collect { sendFlow(LocationStateEvent.OnNetworkEnabled(it)) }
        }
        coroutineScope.launch {
            isPassiveEnabled.state.collect { sendFlow(LocationStateEvent.OnPassiveEnabled(it)) }
        }
        coroutineScope.launch {
            isFusedEnabled.state.collect { sendFlow(LocationStateEvent.OnFusedEnabled(it)) }
        }
    }


    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Logx.d("Location updated: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}m")
            locationChanged.update(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Logx.d("Location status changed: provider=$provider, status=$status")
        }

        override fun onProviderEnabled(provider: String) {
            Logx.i("Location provider enabled: $provider")
            when (provider) {
                LocationManager.GPS_PROVIDER -> isGpsEnabled.update(true)
                LocationManager.NETWORK_PROVIDER -> isNetworkEnabled.update(true)
                LocationManager.PASSIVE_PROVIDER -> isPassiveEnabled.update(true)
                LocationManager.FUSED_PROVIDER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        isFusedEnabled.update(true)
                    }
                }
            }
        }

        override fun onProviderDisabled(provider: String) {
            Logx.i("Location provider disabled: $provider")
            when (provider) {
                LocationManager.GPS_PROVIDER -> isGpsEnabled.update(false)
                LocationManager.NETWORK_PROVIDER -> isNetworkEnabled.update(false)
                LocationManager.PASSIVE_PROVIDER -> isPassiveEnabled.update(false)
                LocationManager.FUSED_PROVIDER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        isFusedEnabled.update(false)
                    }
                }
            }
        }
    }

    private fun sendFlow(event: LocationStateEvent) = coroutineScope.launch { msfUpdate.emit(event) }

    /**
     * This is needed because of TelephonyCallback.CellInfoListener(Telephony.registerCallBack)
     * or
     * PhoneStateListener.LISTEN_CELL_INFO(Telephony.registerListen).
     */
    private var gpsStateBroadcastReceiver : BroadcastReceiver?=null

    private val intentFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)

    public fun registerLocation() {
        unregisterGpsState()
        gpsStateBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                    isGpsEnabled.update(isGpsEnabled())
                    isNetworkEnabled.update(isNetworkEnabled())
                    isPassiveEnabled.update(isPassiveEnabled())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        isFusedEnabled.update(isFusedEnabled())
                    }
                }
            }
        }
        context.registerReceiver(gpsStateBroadcastReceiver, intentFilter)
    }


    public fun getLocationResult(): Result<Location?> {
        return try {
            if (!isAnyEnabled()) {
                val errorMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "No location providers enabled: GPS=${isGpsEnabled()}, Network=${isNetworkEnabled()}, Passive=${isPassiveEnabled()}, Fused=${isFusedEnabled()}"
                } else {
                    "No location providers enabled: GPS=${isGpsEnabled()}, Network=${isNetworkEnabled()}, Passive=${isPassiveEnabled()}"
                }
                Result.failure(SystemServiceException(SystemServiceError.Location.ProviderNotAvailable("any")))
            } else if (context.hasPermissions(ACCESS_COARSE_LOCATION) || context.hasPermissions(ACCESS_FINE_LOCATION)) {
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                Result.success(location)
            } else {
                Result.failure(SystemServiceException(SystemServiceError.Permission.NotGranted(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION))))
            }
        } catch (e: SecurityException) {
            Result.failure(SystemServiceException(SystemServiceError.Permission.NotGranted(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)), e))
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Unknown.Exception(e, "getLocationResult")))
        }
    }

    public fun isGpsEnabledResult(): Result<Boolean> {
        return try {
            Result.success(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Unknown.Exception(e, "isGpsEnabledResult")))
        }
    }

    public fun isNetworkEnabledResult(): Result<Boolean> {
        return try {
            Result.success(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Unknown.Exception(e, "isNetworkEnabledResult")))
        }
    }

    public fun isPassiveEnabledResult(): Result<Boolean> {
        return try {
            Result.success(locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER))
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Unknown.Exception(e, "isPassiveEnabledResult")))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public fun isFusedEnabledResult(): Result<Boolean> {
        return try {
            Result.success(locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER))
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Unknown.Exception(e, "isFusedEnabledResult")))
        }
    }

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    public fun registerLocationUpdateStartResult(locationProvider: String, minTimeMs: Long, minDistanceM: Float): Result<Unit> {
        return try {
            if (!locationManager.isProviderEnabled(locationProvider)) {
                Result.failure(SystemServiceException(SystemServiceError.Location.ProviderDisabled(locationProvider)))
            } else {
                locationManager.requestLocationUpdates(locationProvider, minTimeMs, minDistanceM, locationListener)
                Result.success(Unit)
            }
        } catch (e: SecurityException) {
            Result.failure(SystemServiceException(SystemServiceError.Permission.NotGranted(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)), e))
        } catch (e: IllegalArgumentException) {
            Result.failure(SystemServiceException(SystemServiceError.Location.UpdateStartFailed(locationProvider, e.message ?: "Invalid parameters"), e))
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Location.UpdateStartFailed(locationProvider, e.message ?: "Unknown error"), e))
        }
    }

    public fun calculateDistanceResult(fromLocation: Location, toLocation: Location): Result<Float> {
        return try {
            val distance = fromLocation.distanceTo(toLocation)
            Result.success(distance)
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Location.CalculationFailed("distance calculation", e.message ?: "Unknown error"), e))
        }
    }

    public fun calculateBearingResult(fromLocation: Location, toLocation: Location): Result<Float> {
        return try {
            val bearing = fromLocation.bearingTo(toLocation)
            Result.success(bearing)
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Location.CalculationFailed("bearing calculation", e.message ?: "Unknown error"), e))
        }
    }

    public fun isLocationWithRadiusResult(fromLocation: Location, toLocation: Location, radius: Float): Result<Boolean> {
        return try {
            val distance = calculateDistanceResult(fromLocation, toLocation).getOrThrow()
            Result.success(distance <= radius)
        } catch (e: Exception) {
            when (e) {
                is SystemServiceException -> Result.failure(e)
                else -> Result.failure(SystemServiceException(SystemServiceError.Location.CalculationFailed("radius check", e.message ?: "Unknown error"), e))
            }
        }
    }

    /**
     *
     */
    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    public fun registerLocationUpdateStart(locationProvider: String, minTimeMs: Long, minDistanceM: Float) {
        locationManager.requestLocationUpdates(locationProvider, minTimeMs, minDistanceM, locationListener)
    }

    public override fun onDestroy() {
        unregisterGpsState()
        unregisterLocationUpdateListener()
    }

    public fun unregisterLocationUpdateListener() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Logx.w("Failed to unregister location updates: ${e.message}", e)
        }
    }

    public fun unregisterLocationUpdateListenerResult(): Result<Unit> {
        return try {
            locationManager.removeUpdates(locationListener)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Unknown.Exception(e, "unregisterLocationUpdateListener")))
        }
    }

    public fun unregisterGpsState() {
        gpsStateBroadcastReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Logx.w("Failed to unregister GPS state receiver: ${e.message}", e)
            }
        }
        gpsStateBroadcastReceiver = null
    }

    public fun unregisterGpsStateResult(): Result<Unit> {
        return try {
            gpsStateBroadcastReceiver?.let {
                context.unregisterReceiver(it)
            }
            gpsStateBroadcastReceiver = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SystemServiceException(SystemServiceError.Unknown.Exception(e, "unregisterGpsState")))
        }
    }

    public fun isLocationEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    public fun isGpsEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    public fun isNetworkEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    public fun isPassiveEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)

    @RequiresApi(Build.VERSION_CODES.S)
    public fun isFusedEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)


    public fun isAnyEnabled(): Boolean {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (isLocationEnabled() || isGpsEnabled() || isNetworkEnabled() || isPassiveEnabled() || isFusedEnabled())
        } else {
            (isLocationEnabled() || isGpsEnabled() || isNetworkEnabled() || isPassiveEnabled())
        }
    }

    @SuppressLint("MissingPermission")
    public fun getLocation(): Location? {
        Logx.d("isAnyEnabled() ${isAnyEnabled()} ${context.hasPermissions(ACCESS_COARSE_LOCATION)}, ${context.hasPermissions(ACCESS_FINE_LOCATION)}")
        return if (!isAnyEnabled()) {
            checkSdkVersion(Build.VERSION_CODES.S,
                positiveWork = {
                    Logx.e("can not find location!, isLocationEnabled ${isLocationEnabled()}, isGpsEnabled ${isGpsEnabled()}, isNetworkEnabled ${isNetworkEnabled()}, isPassiveEnabled ${isPassiveEnabled()}, isFusedEnabled ${isFusedEnabled()}")
                },
                negativeWork = {
                    Logx.e("can not find location!, isLocationEnabled ${isLocationEnabled()}, isGpsEnabled ${isGpsEnabled()}, isNetworkEnabled ${isNetworkEnabled()}, isPassiveEnabled ${isPassiveEnabled()}")
                }
            )
            null
        } else if (context.hasPermissions(ACCESS_COARSE_LOCATION)
            || context.hasPermissions(ACCESS_FINE_LOCATION)) {
             locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } else {
            Logx.e("can not find location!, ACCESS_COARSE_LOCATION ${context.hasPermissions(ACCESS_COARSE_LOCATION)}, ACCESS_FINE_LOCATION  ${context.hasPermissions(ACCESS_FINE_LOCATION)}")
            null
        }
    }

    public fun calculateDistance(fromLocation: Location, toLocation: Location): Float =
        fromLocation.distanceTo(toLocation)

    public fun calculateBearing(fromLocation: Location, toLocation: Location): Float =
        fromLocation.bearingTo(toLocation)

    public fun isLocationWithRadius(fromLocation: Location, toLocation: Location, radius: Float): Boolean =
        calculateDistance(fromLocation, toLocation) <= radius
    private val locationStorage by lazy { LocationSharedPreference(context) }

    public fun saveApplyLocationResult(key: String, location: Location): Result<Unit> {
        return locationStorage.saveApplyLocation(key, location)
    }

    public suspend fun saveCommitLocationResult(key: String, location: Location): Result<Unit> {
        return locationStorage.saveCommitLocation(key, location)
    }

    public fun loadLocationResult(key: String): Result<Location?> {
        return locationStorage.loadLocation(key)
    }

    public fun removeLocationResult(key: String): Result<Unit> {
        return locationStorage.removeLocation(key)
    }

    public fun getAllLocationKeysResult(): Result<List<String>> {
        return locationStorage.getAllLocationKeys()
    }

    public fun hasLocationResult(key: String): Result<Boolean> {
        return locationStorage.hasLocation(key)
    }

    public fun clearAllLocationsResult(): Result<Unit> {
        return locationStorage.clearAllLocations()
    }
}