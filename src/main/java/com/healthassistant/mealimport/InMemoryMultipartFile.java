package com.healthassistant.mealimport;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;

class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
        this.name = "images";
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content != null ? content.clone() : null;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getOriginalFilename() { return originalFilename; }

    @Override
    public String getContentType() { return contentType; }

    @Override
    public boolean isEmpty() { return content == null || content.length == 0; }

    @Override
    public long getSize() { return content != null ? content.length : 0; }

    @Override
    public byte[] getBytes() { return content != null ? content : new byte[0]; }

    @Override
    public InputStream getInputStream() { return new ByteArrayInputStream(getBytes()); }

    @Override
    public void transferTo(File dest) throws IOException {
        try (var outputStream = new FileOutputStream(dest)) {
            outputStream.write(getBytes());
        }
    }
}
