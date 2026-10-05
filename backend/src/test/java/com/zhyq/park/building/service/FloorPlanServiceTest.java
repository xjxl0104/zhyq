package com.zhyq.park.building.service;

import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class FloorPlanServiceTest {
    @Test void verifiesImageBytesInsteadOfTrustingFilenameOrContentType() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertThat(FloorPlanService.validateImage(new MockMultipartFile("file", "floor.png", "application/octet-stream", bytes.toByteArray())))
                .isEqualTo("image/png");
        assertThatThrownBy(() -> FloorPlanService.validateImage(new MockMultipartFile("file", "floor.jpg", "image/jpeg", bytes.toByteArray())))
                .isInstanceOf(BizException.class).hasMessageContaining("不一致");
        assertThatThrownBy(() -> FloorPlanService.validateImage(new MockMultipartFile("file", "floor.png", "image/png", "not an image".getBytes())))
                .isInstanceOf(BizException.class).hasMessageContaining("有效图片");
    }
}
