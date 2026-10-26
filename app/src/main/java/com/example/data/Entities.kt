package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Profile
import com.example.model.ProfileType
import com.example.model.Subscription
import com.example.model.UsageRecord

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val subId: String,
    val connectAddr: String,
    val tls: Boolean,
    val sni: String,
    val ws: Boolean,
    val wsPath: String,
    val proxyType: String,
    val proxyHost: String,
    val proxyPort: Int,
    val payload: String,
    val udpgwPort: Int,
    val rawConfig: String,
    val mode: String,
    val proto: String,
    val network: String,
    val security: String,
    val membersCsv: String,
    val createdAt: Long
) {
    fun toModel(): Profile = Profile(
        id = id,
        name = name,
        type = try { ProfileType.valueOf(type) } catch (_: Exception) { ProfileType.SSH },
        host = host,
        port = port,
        username = username,
        password = password,
        subId = subId,
        connectAddr = connectAddr,
        tls = tls,
        sni = sni,
        ws = ws,
        wsPath = wsPath,
        proxyType = proxyType,
        proxyHost = proxyHost,
        proxyPort = proxyPort,
        payload = payload,
        udpgwPort = udpgwPort,
        rawConfig = rawConfig,
        mode = mode,
        proto = proto,
        network = network,
        security = security,
        members = if (membersCsv.isEmpty()) emptyList() else membersCsv.split(","),
        createdAt = createdAt
    )

    companion object {
        fun fromModel(m: Profile): ProfileEntity = ProfileEntity(
            id = m.id,
            name = m.name,
            type = m.type.name,
            host = m.host,
            port = m.port,
            username = m.username,
            password = m.password,
            subId = m.subId,
            connectAddr = m.connectAddr,
            tls = m.tls,
            sni = m.sni,
            ws = m.ws,
            wsPath = m.wsPath,
            proxyType = m.proxyType,
            proxyHost = m.proxyHost,
            proxyPort = m.proxyPort,
            payload = m.payload,
            udpgwPort = m.udpgwPort,
            rawConfig = m.rawConfig,
            mode = m.mode,
            proto = m.proto,
            network = m.network,
            security = m.security,
            membersCsv = m.members.joinToString(","),
            createdAt = m.createdAt
        )
    }
}

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val autoUpdate: Boolean,
    val lastUpdated: Long,
    val profileCount: Int
) {
    fun toModel(): Subscription = Subscription(
        id = id,
        name = name,
        url = url,
        autoUpdate = autoUpdate,
        lastUpdated = lastUpdated,
        profileCount = profileCount
    )

    companion object {
        fun fromModel(m: Subscription): SubscriptionEntity = SubscriptionEntity(
            id = m.id,
            name = m.name,
            url = m.url,
            autoUpdate = m.autoUpdate,
            lastUpdated = m.lastUpdated,
            profileCount = m.profileCount
        )
    }
}

@Entity(tableName = "usage_records")
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val date: String,
    val downBytes: Long,
    val upBytes: Long
) {
    fun toModel(): UsageRecord = UsageRecord(
        id = id,
        profileId = profileId,
        date = date,
        downBytes = downBytes,
        upBytes = upBytes
    )
}
