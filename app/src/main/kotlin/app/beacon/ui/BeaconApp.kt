package app.beacon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.beacon.MainViewModel
import app.beacon.core.geo.CountryDetector
import app.beacon.core.model.DnsMode
import app.beacon.core.model.ProfileKind
import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.Subscription
import app.beacon.vpn.VpnStatus

@Composable
fun BeaconApp(
    viewModel: MainViewModel,
    onConnectRequested: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BeaconTheme {
        BeaconScreen(
            state = state,
            onConnectRequested = onConnectRequested,
            onDisconnect = viewModel::disconnect,
            onTabSelected = viewModel::selectTab,
            onDraftChanged = viewModel::setDraftKey,
            onSaveDraft = viewModel::saveDraftProfile,
            onSelectProfile = viewModel::selectProfile,
            onDeleteProfile = viewModel::deleteProfile,
            onDnsModeChanged = viewModel::setDnsMode,
            onIpv6Changed = viewModel::setIpv6Enabled,
            onAddSubscription = viewModel::addSubscription,
            onRefreshSubscription = viewModel::refreshSubscription,
            onDeleteSubscription = viewModel::deleteSubscription,
            onPingSubscription = viewModel::pingSubscription,
            onPingServer = viewModel::pingServer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeaconScreen(
    state: BeaconUiState,
    onConnectRequested: () -> Unit,
    onDisconnect: () -> Unit,
    onTabSelected: (BeaconTab) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDnsModeChanged: (DnsMode) -> Unit,
    onIpv6Changed: (Boolean) -> Unit,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onPingSubscription: (Subscription) -> Unit,
    onPingServer: (ProxyProfile) -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Beacon", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = state.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            BeaconBottomBar(
                selectedTab = state.selectedTab,
                onTabSelected = onTabSelected
            )
        }
    ) { padding ->
        when (state.selectedTab) {
            BeaconTab.Home -> HomeTab(
                state = state,
                padding = padding,
                onConnectRequested = onConnectRequested,
                onDisconnect = onDisconnect,
                onAddProfile = { onTabSelected(BeaconTab.Profiles) }
            )
            BeaconTab.Profiles -> ProfilesTab(
                state = state,
                padding = padding,
                onDraftChanged = onDraftChanged,
                onSaveDraft = onSaveDraft,
                onSelectProfile = onSelectProfile,
                onDeleteProfile = onDeleteProfile
            )
            BeaconTab.Subscriptions -> SubscriptionsTab(
                state = state,
                padding = padding,
                onAddSubscription = onAddSubscription,
                onRefreshSubscription = onRefreshSubscription,
                onDeleteSubscription = onDeleteSubscription,
                onPingSubscription = onPingSubscription,
                onPingServer = onPingServer,
                onSelectProfile = onSelectProfile
            )
            BeaconTab.Settings -> SettingsTab(
                state = state,
                padding = padding,
                onDnsModeChanged = onDnsModeChanged,
                onIpv6Changed = onIpv6Changed
            )
        }
    }
}

@Composable
private fun HomeTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onConnectRequested: () -> Unit,
    onDisconnect: () -> Unit,
    onAddProfile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ConnectionPanel(
                state = state,
                onConnectRequested = onConnectRequested,
                onDisconnect = onDisconnect,
                onAddProfile = onAddProfile
            )
        }
        item {
            ActiveProfileCard(profile = state.activeProfile)
        }
        state.lastError?.let { error ->
            item {
                ErrorCard(error)
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
    state: BeaconUiState,
    onConnectRequested: () -> Unit,
    onDisconnect: () -> Unit,
    onAddProfile: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusChip(state.statusText)
            Spacer(Modifier.height(20.dp))
            ElevatedButton(
                onClick = {
                    if (state.activeProfile == null) {
                        onAddProfile()
                    } else if (state.status == VpnStatus.Connected) {
                        onDisconnect()
                    } else {
                        onConnectRequested()
                    }
                },
                enabled = !state.isBusy && state.status != VpnStatus.Connecting && state.status != VpnStatus.Disconnecting,
                shape = CircleShape,
                contentPadding = PaddingValues(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (state.activeProfile == null) "Добавить ключ" else primaryActionText(state.status),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Dns,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
private fun ActiveProfileCard(profile: ProxyProfile?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "Активный ключ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = profile?.name ?: "Нет ключа",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = profile?.let { "${it.host}:${it.port}" } ?: "Добавь VLESS Reality ключ",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProfilesTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onDraftChanged: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AddProfileCard(
                value = state.draftKey,
                onValueChanged = onDraftChanged,
                onSave = onSaveDraft,
                enabled = !state.isBusy
            )
        }
        items(state.profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                selected = profile.id == state.activeProfile?.id,
                onSelect = { onSelectProfile(profile.id) },
                onDelete = { onDeleteProfile(profile.id) }
            )
        }
    }
}

@Composable
private fun AddProfileCard(
    value: String,
    onValueChanged: (String) -> Unit,
    onSave: () -> Unit,
    enabled: Boolean
) {
    Card(shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ключ") },
                minLines = 4,
                maxLines = 8
            )
            Button(
                onClick = onSave,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Key, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProxyProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.kind.label() + " · " + profile.hostPort(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onDnsModeChanged: (DnsMode) -> Unit,
    onIpv6Changed: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Text("DNS", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DnsMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.settings.dnsMode == mode,
                                onClick = { onDnsModeChanged(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = DnsMode.entries.size
                                )
                            ) {
                                Text(mode.title)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("IPv6", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (state.settings.ipv6Enabled) "Включён" else "Выключен",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.settings.ipv6Enabled,
                        onCheckedChange = onIpv6Changed
                    )
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Text("Состояние", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(state.statusText)
                    state.lastError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun BeaconBottomBar(
    selectedTab: BeaconTab,
    onTabSelected: (BeaconTab) -> Unit
) {
    NavigationBar {
        BeaconTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(tab.title) }
            )
        }
    }
}

private fun primaryActionText(status: VpnStatus): String {
    return when (status) {
        VpnStatus.Connected -> "Отключить"
        VpnStatus.Connecting -> "Подключение"
        VpnStatus.Disconnecting -> "Отключение"
        VpnStatus.Disconnected -> "Подключить"
    }
}

private fun ProfileKind.label(): String {
    return when (this) {
        ProfileKind.VlessReality -> "VLESS Reality"
    }
}

private fun ProxyProfile.hostPort(): String {
    return if (port > 0) "$host:$port" else host
}

private fun BeaconTab.icon(): ImageVector {
    return when (this) {
        BeaconTab.Home -> Icons.Outlined.Home
        BeaconTab.Profiles -> Icons.Outlined.Key
        BeaconTab.Subscriptions -> Icons.Outlined.Public
        BeaconTab.Settings -> Icons.Outlined.Settings
    }
}

private fun flagEmoji(code: String?): String {
    if (code == null || code.length != 2) return "🌐"
    val upper = code.uppercase()
    if (upper.any { it !in 'A'..'Z' }) return "🌐"
    val builder = StringBuilder()
    upper.forEach { builder.appendCodePoint(0x1F1E6 + (it - 'A')) }
    return builder.toString()
}

@Composable
private fun SubscriptionsTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onPingSubscription: (Subscription) -> Unit,
    onPingServer: (ProxyProfile) -> Unit,
    onSelectProfile: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AddSubscriptionCard(
                onAdd = onAddSubscription,
                enabled = !state.isBusy
            )
        }
        state.lastError?.let { error ->
            item { ErrorCard(error) }
        }
        if (state.subscriptions.isEmpty()) {
            item {
                Text(
                    text = "Подписок пока нет. Вставь ссылку подписки выше — она развернётся в список серверов.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.subscriptions, key = { it.id }) { subscription ->
            SubscriptionCard(
                subscription = subscription,
                activeProfileId = state.activeProfile?.id,
                pingResults = state.pingResults,
                pingingIds = state.pingingIds,
                busy = state.isBusy,
                onRefresh = { onRefreshSubscription(subscription) },
                onDelete = { onDeleteSubscription(subscription.id) },
                onPingAll = { onPingSubscription(subscription) },
                onPingServer = onPingServer,
                onSelectServer = { onSelectProfile(it.id) }
            )
        }
    }
}

@Composable
private fun AddSubscriptionCard(
    onAdd: (String) -> Unit,
    enabled: Boolean
) {
    var url by remember { mutableStateOf("") }
    Card(shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка подписки") },
                placeholder = { Text("https://...") },
                singleLine = true
            )
            Button(
                onClick = {
                    onAdd(url)
                    url = ""
                },
                enabled = enabled && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить подписку")
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    activeProfileId: String?,
    pingResults: Map<String, Long?>,
    pingingIds: Set<String>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onPingAll: () -> Unit,
    onPingServer: (ProxyProfile) -> Unit,
    onSelectServer: (ProxyProfile) -> Unit
) {
    Card(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = subscription.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${subscription.profiles.size} серверов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPingAll, enabled = !busy) {
                    Icon(Icons.Outlined.Bolt, contentDescription = "Пинг всех")
                }
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                }
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                }
            }
            if (subscription.profiles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
            subscription.profiles.forEach { server ->
                ServerRow(
                    server = server,
                    selected = server.id == activeProfileId,
                    pinging = server.id in pingingIds,
                    hasPing = pingResults.containsKey(server.id),
                    pingMs = pingResults[server.id],
                    onSelect = { onSelectServer(server) },
                    onPing = { onPingServer(server) }
                )
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: ProxyProfile,
    selected: Boolean,
    pinging: Boolean,
    hasPing: Boolean,
    pingMs: Long?,
    onSelect: () -> Unit,
    onPing: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = flagEmoji(CountryDetector.detect(server.name)),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${server.host}:${server.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onPing) {
            Text(pingText(pinging, hasPing, pingMs))
        }
        if (selected) {
            AssistChip(onClick = onSelect, label = { Text("активен") })
        } else {
            OutlinedButton(onClick = onSelect) {
                Text("выбрать")
            }
        }
    }
}

private fun pingText(pinging: Boolean, hasPing: Boolean, pingMs: Long?): String = when {
    pinging -> "…"
    !hasPing -> "пинг"
    pingMs == null -> "—"
    else -> "$pingMs ms"
}
