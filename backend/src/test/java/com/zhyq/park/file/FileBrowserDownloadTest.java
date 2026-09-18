package com.zhyq.park.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.auth.*;
import com.zhyq.park.common.accesslog.*;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.exception.GlobalExceptionHandler;
import com.zhyq.park.file.controller.FileController;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileDownloadTicketService;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FileController.class, properties = {
    "zhyq.jwt.secret=download-integration-test-secret-at-least-32-bytes"
})
@ContextConfiguration(classes = FileController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class,
    RestAuthEntryPoint.class, RestAccessDeniedHandler.class, AccessLogFilter.class,
    FileDownloadTicketService.class, GlobalExceptionHandler.class})
class FileBrowserDownloadTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @MockBean SysFileMapper files;
    @MockBean FileStorageService storage;
    @MockBean AccessLogWriter logWriter;
    @MockBean ExcludeListHolder excludes;
    @MockBean RouteModuleHolder routes;
    @MockBean SysUserMapper users;
    @TempDir Path directory;
    private final byte[] content = "%PDF-1.7\ncomplete contract\n%%EOF".getBytes();
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        Path pdf = directory.resolve("contract.pdf");
        Files.write(pdf, content);
        SysFile file = new SysFile();
        file.setId(35L);
        file.setOriginalName("合同.pdf");
        file.setStorePath("2026/09/contract.pdf");
        file.setContentType("application/pdf");
        file.setFileSize((long) content.length);
        when(files.selectById(35L)).thenReturn(file);
        when(storage.resolveExisting(file.getStorePath())).thenReturn(pdf);
        bearer = "Bearer " + jwt.issue(1L, "reader", List.of());
    }

    private String issue() throws Exception {
        String body = mvc.perform(post("/file/download-ticket/35").header("Authorization", bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("ticket").asText();
    }

    @Test
    void issuingRequiresLoginAndOldDownloadRemainsProtected() throws Exception {
        mvc.perform(post("/file/download-ticket/35")).andExpect(status().isUnauthorized());
        mvc.perform(get("/file/download/35")).andExpect(status().isUnauthorized());
        mvc.perform(post("/file/browser-download/35")).andExpect(status().isUnauthorized());
        verifyNoInteractions(files, storage);
    }

    @Test
    void browserCanDownloadWithoutBearerButCannotReplayTicket() throws Exception {
        String ticket = issue();
        mvc.perform(get("/file/browser-download/35").param("ticket", ticket))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Length", Integer.toString(content.length)))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(content));
        mvc.perform(get("/file/browser-download/35").param("ticket", ticket))
            .andExpect(status().isForbidden()).andExpect(content().string(""));
    }

    @Test
    void ticketDoesNotGrantAccessToOtherFilesOrOtherApis() throws Exception {
        String ticket = issue();
        mvc.perform(get("/file/browser-download/36").param("ticket", ticket))
            .andExpect(status().isForbidden());
        verify(files, never()).selectById(36L);
        mvc.perform(get("/file/list").header("Authorization", "Bearer " + ticket)
                .param("bizType", "contract").param("bizId", "8"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrInvalidTicketCannotReadFileEvenWithLogin() throws Exception {
        mvc.perform(get("/file/browser-download/35")).andExpect(status().isForbidden());
        mvc.perform(get("/file/browser-download/35").param("ticket", "invalid")
                .header("Authorization", bearer)).andExpect(status().isForbidden());
        verifyNoInteractions(files, storage);
    }

    @Test
    void removedFileReturnsReal404InsteadOfDownloadingErrorJson() throws Exception {
        String ticket = issue();
        when(files.selectById(35L)).thenReturn(null);
        mvc.perform(get("/file/browser-download/35").param("ticket", ticket))
            .andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    @Test
    void missingPhysicalFileFailsBeforeBrowserHandoff() throws Exception {
        when(storage.resolveExisting(anyString())).thenThrow(new BizException("附件文件不存在或已被清理"));
        mvc.perform(post("/file/download-ticket/35").header("Authorization", bearer))
            .andExpect(jsonPath("$.message").value("附件文件不存在或已被清理"))
            .andExpect(jsonPath("$.data.ticket").doesNotExist());
    }
}
