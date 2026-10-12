package org.sunbird.workflow.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.workflow.config.Configuration;
import org.sunbird.workflow.models.SBApiResponse;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    @InjectMocks
    StorageServiceImpl service;

    @Mock
    BaseStorageService storageService;

    @Mock
    Configuration configuration;

    MockMultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        multipartFile = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());

        // Constructor injection no longer field-injects storageService; wire the mock directly
        // so init() short-circuits instead of building a real storage client.
        ReflectionTestUtils.setField(service, "storageService", storageService);

        // manually trigger @PostConstruct
        service.init();

    }

    @Test
    void testUploadFile_MultipartFile_Success() throws Exception {
        when(storageService.upload(any(), any(), any(), any(), any(), any(), any())).thenReturn("http://uploaded.url");

        SBApiResponse response = service.uploadFile(multipartFile, "folder", "container");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey("name"));
        assertTrue(response.getResult().containsKey("url"));
    }

    @Test
    void testUploadFile_MultipartFile_Failure() throws Exception {
        MockMultipartFile badFile = mock(MockMultipartFile.class);
        when(badFile.getOriginalFilename()).thenThrow(new RuntimeException("fail"));

        SBApiResponse response = service.uploadFile(badFile, "folder", "container");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("FAILED", response.getParams().getStatus());
    }

    @Test
    void testUploadFile_File_Success() throws Exception {
        File file = File.createTempFile("test", ".txt");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("Hello".getBytes());
        }
        when(storageService.upload(any(), any(), any(), any(), any(), any(), any())).thenReturn("http://uploaded.url");

        SBApiResponse response = service.uploadFile(file, "folder", "container");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey("name"));
        assertTrue(response.getResult().containsKey("url"));
        assertFalse(file.exists()); // should be deleted in finally
    }

    @Test
    void testUploadFile_File_Failure() throws Exception {
        File file = File.createTempFile("test", ".txt");
        doThrow(new RuntimeException("upload fail")).when(storageService).upload(any(), any(), any(), any(), any(), any(), any());

        SBApiResponse response = service.uploadFile(file, "folder", "container");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("FAILED", response.getParams().getStatus());
        assertFalse(file.exists()); // deleted
    }

    @Test
    void testDownloadFile_Success() {
        doNothing().when(storageService).download(any(), any(), any(), any());

        SBApiResponse response = service.downloadFile("myfile.txt");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testDownloadFile_Failure() {
        doThrow(new RuntimeException("fail")).when(storageService).download(any(), any(), any(), any());

        SBApiResponse response = service.downloadFile("myfile.txt");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("FAILED", response.getParams().getStatus());
    }
}
