package com.ghalbitnet.meshx2.vpn

object TcpFlagParser {

    data class Flags(
        val syn: Boolean,
        val ack: Boolean,
        val psh: Boolean,
        val fin: Boolean,
        val rst: Boolean
    ) {
        fun label(): String {
            return buildList {
                if (syn) add("SYN")
                if (ack) add("ACK")
                if (psh) add("PSH")
                if (fin) add("FIN")
                if (rst) add("RST")
            }.joinToString("+").ifBlank { "NONE" }
        }
    }

    fun parse(flags: Int): Flags {
        return Flags(
            syn = (flags and TcpHeaderBuilder.FLAG_SYN) != 0,
            ack = (flags and TcpHeaderBuilder.FLAG_ACK) != 0,
            psh = (flags and TcpHeaderBuilder.FLAG_PSH) != 0,
            fin = (flags and TcpHeaderBuilder.FLAG_FIN) != 0,
            rst = (flags and TcpHeaderBuilder.FLAG_RST) != 0
        )
    }
}
