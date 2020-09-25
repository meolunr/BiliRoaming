package com.google.protobuf;

public interface MessageLite {

    Builder toBuilder();

    public interface Builder {
        MessageLite build();
    }
}