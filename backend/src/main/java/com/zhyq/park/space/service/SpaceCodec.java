package com.zhyq.park.space.service;

public final class SpaceCodec {
    public static final String PREFIX_PROJECT = "P";
    public static final String PREFIX_BUILDING = "B";
    public static final String PREFIX_FLOOR = "F";
    public static final String PREFIX_ROOM = "R";

    private SpaceCodec() {}

    public static String childCode(String parentCode, String typePrefix, String rawCode, Long fallbackId) {
        String seg = (rawCode == null || rawCode.isBlank()) ? "#" + fallbackId : rawCode.trim();
        if (parentCode == null || parentCode.isBlank()) {
            return typePrefix + seg;
        }
        return parentCode + "-" + typePrefix + seg;
    }

    public static String buildPath(String parentPath, Long selfId) {
        if (parentPath == null || parentPath.isBlank()) {
            return "/" + selfId + "/";
        }
        return parentPath + selfId + "/";
    }
}
