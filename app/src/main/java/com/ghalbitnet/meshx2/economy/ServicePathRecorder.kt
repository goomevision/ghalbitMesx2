package com.ghalbitnet.meshx2.economy

import android.content.Context
import com.ghalbitnet.meshx2.core.network.TransportPreference
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.routing.MeshRegistry
import org.json.JSONArray
import org.json.JSONObject

object ServicePathRecorder {

    private const val PREFS_NAME = "service_path_recorder"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 160

    private var appContext: Context? = null
    private var localNodeId: String = ""

    enum class EventType {
        SEND,
        RELAY,
        RECEIVE
    }

    data class PathEvent(
        val packetId: String,
        val packetType: String,
        val nodeId: String,
        val nodeName: String,
        val peerAddress: String,
        val modeLabel: String,
        val eventType: EventType,
        val hopCount: Int,
        val timestamp: Long
    )

    fun initialize(
        context: Context,
        nodeId: String
    ) {
        appContext = context.applicationContext
        localNodeId = nodeId
    }

    fun recordSend(
        packet: MeshPacket,
        nextHopIp: String
    ) {
        record(
            packet = packet,
            peerAddress = nextHopIp,
            eventType = EventType.SEND
        )
    }

    fun recordRelay(
        packet: MeshPacket,
        nextHopIp: String
    ) {
        record(
            packet = packet,
            peerAddress = nextHopIp,
            eventType = EventType.RELAY
        )
    }

    fun recordReceive(
        packet: MeshPacket
    ) {
        record(
            packet = packet,
            peerAddress = packet.source,
            eventType = EventType.RECEIVE
        )
    }

    fun recentEvents(
        context: Context,
        limit: Int = 40
    ): List<PathEvent> {
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val array =
            JSONArray(prefs.getString(KEY_EVENTS, "[]"))

        val items = mutableListOf<PathEvent>()

        for (index in array.length() - 1 downTo 0) {
            items += deserialize(array.getJSONObject(index))
            if (items.size >= limit) {
                break
            }
        }

        return items
    }

    fun recentRelayParticipants(
        context: Context,
        limit: Int = 6
    ): List<ServiceParticipant> {
        val relays =
            recentEvents(context, 80)
                .filter { it.eventType == EventType.RELAY }
                .distinctBy { "${it.nodeId}|${it.peerAddress}" }
                .take(limit)

        return relays.map {
            ServiceParticipant(
                nodeId = if (it.nodeId.isBlank()) it.peerAddress else it.nodeId,
                nodeName = if (it.nodeName.isBlank()) it.peerAddress else it.nodeName,
                nodeAddress = it.peerAddress,
                role = ServiceRole.RELAY,
                local = it.nodeId == localNodeId,
                trustScore = MeshRegistry.getNode(it.peerAddress)?.trusted ?: 60
            )
        }
    }

    private fun record(
        packet: MeshPacket,
        peerAddress: String,
        eventType: EventType
    ) {
        val context = appContext ?: return
        val prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val peerNode =
            MeshRegistry.getNode(peerAddress)

        val nodeId =
            peerNode?.name ?: if (eventType == EventType.RECEIVE) packet.source else packet.destination

        val event =
            PathEvent(
                packetId = packet.packetId,
                packetType = packet.type,
                nodeId = nodeId,
                nodeName = peerNode?.name ?: nodeId,
                peerAddress = peerAddress,
                modeLabel = TransportPreference.modeForAddress(peerAddress).label,
                eventType = eventType,
                hopCount = packet.hopCount,
                timestamp = System.currentTimeMillis()
            )

        val current =
            JSONArray(prefs.getString(KEY_EVENTS, "[]"))

        current.put(serialize(event))

        val trimmed =
            JSONArray().apply {
                val start = maxOf(0, current.length() - MAX_EVENTS)
                for (index in start until current.length()) {
                    put(current.getJSONObject(index))
                }
            }

        prefs.edit()
            .putString(KEY_EVENTS, trimmed.toString())
            .apply()
    }

    private fun serialize(
        event: PathEvent
    ): JSONObject {
        return JSONObject()
            .put("packetId", event.packetId)
            .put("packetType", event.packetType)
            .put("nodeId", event.nodeId)
            .put("nodeName", event.nodeName)
            .put("peerAddress", event.peerAddress)
            .put("modeLabel", event.modeLabel)
            .put("eventType", event.eventType.name)
            .put("hopCount", event.hopCount)
            .put("timestamp", event.timestamp)
    }

    private fun deserialize(
        source: JSONObject
    ): PathEvent {
        return PathEvent(
            packetId = source.optString("packetId"),
            packetType = source.optString("packetType"),
            nodeId = source.optString("nodeId"),
            nodeName = source.optString("nodeName"),
            peerAddress = source.optString("peerAddress"),
            modeLabel = source.optString("modeLabel"),
            eventType = EventType.valueOf(source.optString("eventType", EventType.RELAY.name)),
            hopCount = source.optInt("hopCount"),
            timestamp = source.optLong("timestamp")
        )
    }
}

