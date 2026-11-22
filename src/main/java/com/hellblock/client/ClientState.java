package com.hellblock.client;

/** Клиентское состояние скверны (приходит с сервера). */
public final class ClientState {
    public static volatile int stage = 0;
    public static volatile int count = 0;

    private ClientState() {
    }
}
