package app.beacon.data

import app.beacon.core.model.ProxyProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(): Flow<List<ProxyProfile>>
    fun observeActiveProfileId(): Flow<String?>
    fun observeSettings(): Flow<BeaconSettings>
    suspend fun saveProfile(profile: ProxyProfile)
    suspend fun deleteProfile(profileId: String)
    suspend fun setActiveProfile(profileId: String)
    suspend fun updateSettings(settings: BeaconSettings)
    suspend fun currentActiveProfile(): ProxyProfile?
    suspend fun currentSettings(): BeaconSettings
}
