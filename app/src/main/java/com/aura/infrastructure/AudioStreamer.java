package com.aura.infrastructure;

public interface AudioStreamer extends AutoCloseable {
    void start();
    void stop();
    default void pause() { stop(); }
    default void resume() { start(); }
    @Override
    void close();
}
