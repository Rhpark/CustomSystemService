package kr.open.library.systemmanager.info.location

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.SystemServiceError
import kr.open.library.systemmanager.base.SystemServiceException

/**
 * SharedPreferences wrapper for storing and retrieving Location objects.
 * Location 객체를 저장하고 검색하기 위한 SharedPreferences 래퍼입니다.
 */
public class LocationSharedPreference(private val context: Context) {
    
    private companion object {
        private const val PREF_NAME = "location_preferences"
        private const val LATITUDE_SUFFIX = "_latitude"
        private const val LONGITUDE_SUFFIX = "_longitude"
        private const val ACCURACY_SUFFIX = "_accuracy"
        private const val TIME_SUFFIX = "_time"
        private const val PROVIDER_SUFFIX = "_provider"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Saves location synchronously using apply().
     * apply()를 사용하여 위치를 동기적으로 저장합니다.
     */
    public fun saveApplyLocation(key: String, location: Location): Result<Unit> {
        return try {
            prefs.edit().apply {
                putFloat("$key$LATITUDE_SUFFIX", location.latitude.toFloat())
                putFloat("$key$LONGITUDE_SUFFIX", location.longitude.toFloat())
                putFloat("$key$ACCURACY_SUFFIX", location.accuracy)
                putLong("$key$TIME_SUFFIX", location.time)
                putString("$key$PROVIDER_SUFFIX", location.provider)
                apply()
            }
            Logx.d("Location saved with key: $key")
            Result.success(Unit)
        } catch (e: Exception) {
            Logx.e("Failed to save location with key: $key", e)
            Result.failure(SystemServiceException(
                SystemServiceError.Unknown.Exception(e, "saveApplyLocation"),
                e
            ))
        }
    }
    
    /**
     * Saves location asynchronously using commit().
     * commit()을 사용하여 위치를 비동기적으로 저장합니다.
     */
    public suspend fun saveCommitLocation(key: String, location: Location): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val success = prefs.edit().apply {
                    putFloat("$key$LATITUDE_SUFFIX", location.latitude.toFloat())
                    putFloat("$key$LONGITUDE_SUFFIX", location.longitude.toFloat())
                    putFloat("$key$ACCURACY_SUFFIX", location.accuracy)
                    putLong("$key$TIME_SUFFIX", location.time)
                    putString("$key$PROVIDER_SUFFIX", location.provider)
                }.commit()
                
                if (success) {
                    Logx.d("Location committed with key: $key")
                    Result.success(Unit)
                } else {
                    Logx.e("Failed to commit location with key: $key")
                    Result.failure(SystemServiceException(
                        SystemServiceError.Resource.Locked("SharedPreferences", "commit failed")
                    ))
                }
            } catch (e: Exception) {
                Logx.e("Exception while committing location with key: $key", e)
                Result.failure(SystemServiceException(
                    SystemServiceError.Unknown.Exception(e, "saveCommitLocation"),
                    e
                ))
            }
        }
    }
    
    /**
     * Loads location from SharedPreferences.
     * SharedPreferences에서 위치를 로드합니다.
     */
    public fun loadLocation(key: String): Result<Location?> {
        return try {
            val latitude = prefs.getFloat("$key$LATITUDE_SUFFIX", Float.NaN)
            val longitude = prefs.getFloat("$key$LONGITUDE_SUFFIX", Float.NaN)
            
            if (latitude.isNaN() || longitude.isNaN()) {
                Logx.d("Location not found for key: $key")
                Result.success(null)
            } else {
                val location = Location(prefs.getString("$key$PROVIDER_SUFFIX", "saved") ?: "saved").apply {
                    setLatitude(latitude.toDouble())
                    setLongitude(longitude.toDouble())
                    accuracy = prefs.getFloat("$key$ACCURACY_SUFFIX", 0f)
                    time = prefs.getLong("$key$TIME_SUFFIX", System.currentTimeMillis())
                }
                Logx.d("Location loaded for key: $key")
                Result.success(location)
            }
        } catch (e: Exception) {
            Logx.e("Failed to load location with key: $key", e)
            Result.failure(SystemServiceException(
                SystemServiceError.Unknown.Exception(e, "loadLocation"),
                e
            ))
        }
    }
    
    /**
     * Removes stored location.
     * 저장된 위치를 제거합니다.
     */
    public fun removeLocation(key: String): Result<Unit> {
        return try {
            prefs.edit().apply {
                remove("$key$LATITUDE_SUFFIX")
                remove("$key$LONGITUDE_SUFFIX")
                remove("$key$ACCURACY_SUFFIX")
                remove("$key$TIME_SUFFIX")
                remove("$key$PROVIDER_SUFFIX")
                apply()
            }
            Logx.d("Location removed for key: $key")
            Result.success(Unit)
        } catch (e: Exception) {
            Logx.e("Failed to remove location with key: $key", e)
            Result.failure(SystemServiceException(
                SystemServiceError.Unknown.Exception(e, "removeLocation"),
                e
            ))
        }
    }
    
    /**
     * Gets all stored location keys.
     * 저장된 모든 위치 키를 가져옵니다.
     */
    public fun getAllLocationKeys(): Result<List<String>> {
        return try {
            val keys = prefs.all.keys
                .filter { it.endsWith(LATITUDE_SUFFIX) }
                .map { it.removeSuffix(LATITUDE_SUFFIX) }
                .distinct()
            
            Logx.d("Found ${keys.size} stored locations")
            Result.success(keys)
        } catch (e: Exception) {
            Logx.e("Failed to get location keys", e)
            Result.failure(SystemServiceException(
                SystemServiceError.Unknown.Exception(e, "getAllLocationKeys"),
                e
            ))
        }
    }
    
    /**
     * Checks if location exists for the given key.
     * 주어진 키에 대한 위치가 존재하는지 확인합니다.
     */
    public fun hasLocation(key: String): Result<Boolean> {
        return try {
            val hasLatitude = prefs.contains("$key$LATITUDE_SUFFIX")
            val hasLongitude = prefs.contains("$key$LONGITUDE_SUFFIX")
            Result.success(hasLatitude && hasLongitude)
        } catch (e: Exception) {
            Logx.e("Failed to check location existence with key: $key", e)
            Result.failure(SystemServiceException(
                SystemServiceError.Unknown.Exception(e, "hasLocation"),
                e
            ))
        }
    }
    
    /**
     * Clears all stored locations.
     * 저장된 모든 위치를 지웁니다.
     */
    public fun clearAllLocations(): Result<Unit> {
        return try {
            val editor = prefs.edit()
            val allKeys = prefs.all.keys
            
            allKeys.forEach { key ->
                if (key.endsWith(LATITUDE_SUFFIX) || 
                    key.endsWith(LONGITUDE_SUFFIX) ||
                    key.endsWith(ACCURACY_SUFFIX) ||
                    key.endsWith(TIME_SUFFIX) ||
                    key.endsWith(PROVIDER_SUFFIX)) {
                    editor.remove(key)
                }
            }
            
            editor.apply()
            Logx.d("All locations cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Logx.e("Failed to clear all locations", e)
            Result.failure(SystemServiceException(
                SystemServiceError.Unknown.Exception(e, "clearAllLocations"),
                e
            ))
        }
    }
}