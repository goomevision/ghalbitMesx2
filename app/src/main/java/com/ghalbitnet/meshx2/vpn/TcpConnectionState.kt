package com.ghalbitnet.meshx2.vpn

enum class TcpConnectionState {
    CLOSED,
    SYN_SENT,
    SYN_RECEIVED,
    ESTABLISHED,
    FIN_WAIT,
    CLOSE_WAIT,
    LAST_ACK,
    TIME_WAIT,
    RESET
}
