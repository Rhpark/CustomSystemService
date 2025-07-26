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
import kr.open.library.systemmanager.extenstions.checkSdkVersion
import kr.open.library.systemmanager.extenstions.getLocationManager

public open class LocationStateInfo(
    context: Context,
    private val coroutineScope: CoroutineScope
) :
    BaseSystemService(context, listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)) {

    public val locationManager: LocationManager by lazy { context.getLocationManager() }

    private val msfUpdate: MutableStateFlow<LocationStateEvent> = MutableStateFlow(LocationStateEvent.OnGpsEnabled(isGpsEnabled()))
    public val sfUpdate: StateFlow<LocationStateEvent> = msfUpdate.asStateFlow()

    private val locationChanged = DataUpdate<Location?>(getLocation()){ sendFlow(LocationStateEvent.OnLocationChanged(it))}

    private val isGpsEnabled = DataUpdate<Boolean>(isGpsEnable()){ sendFlow(LocationStateEvent.OnGpsEnabled(it))}

    private val isNetworkEnabled = DataUpdate<Boolean>(isNetworkEnable()){ sendFlow(LocationStateEvent.OnNetworkEnabled(it))}

    private val isPassiveEnabled = DataUpdate<Boolean>(isPassiveEnable()){ sendFlow(LocationStateEvent.OnPassiveEnabled(it))}

    private val isFusedEnabled = DataUpdate<Boolean>(isFusedEnable()){ sendFlow(LocationStateEvent.OnFusedEnabled(it))}


    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            locationChanged.update(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {        }

        override fun onProviderEnabled(provider: String) {        }

        override fun onProviderDisabled(provider: String) {        }
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
                    isGpsEnabled.update(isGpsEnable())
                    isNetworkEnabled.update(isNetworkEnable())
                    isPassiveEnabled.update(isPassiveEnable())
                    isFusedEnabled.update(isFusedEnable())
                }
            }
        }
        context.registerReceiver(gpsStateBroadcastReceiver, intentFilter)
    }

    public fun isGpsEnable():Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    public fun isNetworkEnable():Boolean = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    public fun isPassiveEnable():Boolean = locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
    public fun isFusedEnable():Boolean = locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)

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
        } catch (e:Exception) {        }
    }

    public fun unregisterGpsState() {
        gpsStateBroadcastReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e:Exception) {}
        }
        gpsStateBroadcastReceiver = null
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
//
//    public fun saveApplyLocation(key: String, location: Location) {
//        LocationSharedPreference(context).saveApplyLocation(key,location)
//    }
//
//    public suspend fun saveCommitLocation(key:String, location: Location) {
//        LocationSharedPreference(context).saveCommitLocation(key,location)
//    }
//
//    public fun loadLocation(key: String): Location? = LocationSharedPreference(context).loadLocation(key)
}