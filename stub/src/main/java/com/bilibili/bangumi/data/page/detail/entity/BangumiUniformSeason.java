package com.bilibili.bangumi.data.page.detail.entity;

import java.util.ArrayList;
import java.util.List;

public class BangumiUniformSeason {

    public ArrayList<?> episodes;
    public List<?> modules;
    public Right rights;
    public BangumiSeasonLimit seasonLimit;

    public static class BangumiSeasonLimit {
        public BangumiSeasonLimit() {
            throw new RuntimeException("Stub!");
        }
    }

    public static class Right {
        public boolean allowDownload;
        public boolean areaLimit;
    }
}