package ru.kuranov.handler;

import ru.kuranov.message.AbstractMessage;

public interface OnMessageReceived {
    void onReceive(AbstractMessage msg);
}
