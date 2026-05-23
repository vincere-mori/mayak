package app.beacon.data

import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.Subscription
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(): Flow<List<ProxyProfile>>
    fun observeSubscriptions(): Flow<List<Subscription>>
    fun observeActiveProfileId(): Flow<String?>
    fun observeSettings(): Flow<BeaconSettings>
    suspend fun saveProfile(profile: ProxyProfile)
    suspend fun deleteProfile(profileId: String)
    suspend fun saveSubscription(subscription: Subscription)
    suspend fun deleteSubscription(subscriptionId: String)
    suspend fun setActiveProfile(profileId: String)
    suspend fun updateSettings(settings: BeaconSettings)
    suspend fun currentActiveProfile(): ProxyProfile?
    suspend fun currentSettings(): BeaconSettings
}
