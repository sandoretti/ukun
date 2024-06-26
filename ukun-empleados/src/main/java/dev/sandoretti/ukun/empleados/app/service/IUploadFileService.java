package dev.sandoretti.ukun.empleados.app.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface IUploadFileService
{
    public String copy(MultipartFile file, String name) throws IOException;

    public boolean delete(String fileName);
}
