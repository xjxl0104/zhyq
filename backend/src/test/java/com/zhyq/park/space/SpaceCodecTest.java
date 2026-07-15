package com.zhyq.park.space;

import com.zhyq.park.space.service.SpaceCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaceCodecTest {

    @Test
    void projectCode_usesPrefix_whenNoParent() {
        assertEquals("PA01", SpaceCodec.childCode(null, SpaceCodec.PREFIX_PROJECT, "A01", 12L));
    }

    @Test
    void childCode_appendsUnderParent() {
        assertEquals("PA01-B1", SpaceCodec.childCode("PA01", SpaceCodec.PREFIX_BUILDING, "1", 5L));
    }

    @Test
    void childCode_fallsBackToId_whenRawBlank() {
        assertEquals("PA01-B#5", SpaceCodec.childCode("PA01", SpaceCodec.PREFIX_BUILDING, "  ", 5L));
    }

    @Test
    void buildPath_root() {
        assertEquals("/7/", SpaceCodec.buildPath(null, 7L));
    }

    @Test
    void buildPath_child() {
        assertEquals("/7/23/", SpaceCodec.buildPath("/7/", 23L));
    }
}
