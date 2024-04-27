package dev.sandoretti.ukun.empleados.app.models.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class UploadFileServiceImpl implements IUploadFileService
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final static String UPLOADS_FOLDER = "/opt/resources/uploads/";

    @Override
    public String copy(MultipartFile file, String name) throws IOException
    {
        String fileName = generateFileName(file.getOriginalFilename(), name);

        Path absolutePath = getPath(fileName);

        // Copiamos el archivo a la ruta
        Files.copy(file.getInputStream(), absolutePath);

        return fileName;
    }

    @Override
    public boolean delete(String fileName)
    {
        Path absolutePath = getPath(fileName);
        File file = absolutePath.toFile();

        return file.exists() && file.canRead() && file.delete();
    }

    /**
     * Devuelve el path con la carpeta de cargas del nombre del archivo
     * @param fileName Nombre del archivo
     * @return Path
     */
    private Path getPath(String fileName)
    {
        return Paths.get(UPLOADS_FOLDER).resolve(fileName).toAbsolutePath();
    }

    /**
     * Obtiene la extension del archivo
     * @param fileName Nombre del archivo
     * @return Cadena de la extension
     */
    private String getExtension(String fileName)
    {
        return fileName != null ? fileName.substring(fileName.lastIndexOf(".")) : "";
    }

    /**
     * Genera un nombre para el archivo
     * @param fileNameOriginal Nombre del archivo original
     * @param name Nombre a poner en el archivo
     * @return Nombre de archivo generado
     */
    private String generateFileName(String fileNameOriginal, String name)
    {
        String uuid = UUID.randomUUID().toString();
        String newName = name.replace(" ", "-");
        String extension = getExtension(fileNameOriginal);

        return uuid.concat("-").concat(newName).concat(extension);
    }
}
