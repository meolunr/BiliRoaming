package com.bapis.bilibili.pgc.gateway.player.v1;

import com.bapis.bilibili.app.playurl.v1.VideoInfo;
import com.google.protobuf.GeneratedMessageLite;

public final class PlayViewReply extends GeneratedMessageLite {

    private PlayViewReply() {
    }

    public boolean hasVideoInfo() {
        throw new RuntimeException("Stub!");
    }

    public static Builder newBuilder() {
        throw new RuntimeException("Stub!");
    }

    public static final class Builder extends GeneratedMessageLite.Builder<PlayViewReply> {

        public Builder setPlayConf(PlayAbilityConf conf) {
            throw new RuntimeException("Stub!");
        }

        public Builder setVideoInfo(VideoInfo videoInfo) {
            throw new RuntimeException("Stub!");
        }
    }
}