package app.mayak.data

import app.mayak.core.model.ProxyProfile
import app.mayak.core.model.Subscription
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(): Flow<List<ProxyProfile>>
    fun observeSubscriptions(): Flow<List<Subscription>>
    fun observeActiveProfileId(): Flow<String?>
    fun observeSettings(): Flow<MayakSettings>
    suspend fun saveProfile(profile: ProxyProfile)
    suspend fun deleteProfile(profileId: String)
    suspend fun saveSubscription(subscription: Subscription)
    suspend fun deleteSubscription(subscriptionId: String)
    suspend fun setActiveProfile(profileId: String)
    suspend fun updateSettings(settings: MayakSettings)
    suspend fun currentActiveProfile(): ProxyProfile?
    suspend fun currentSettings(): MayakSettings
}
