package zf.nastest.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
@RequestMapping("/api/files")
public class FileController {

    @Value("${file.upload-dir}")
    private String FILE_DIR;

    // 获取文件列表
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, String>>> getFileList() {
        File directory = new File(FILE_DIR);
        List<Map<String, String>> fileList = Arrays.stream(Objects.requireNonNull(directory.listFiles()))
                .map(file -> {
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("name", file.getName());
                    try {
                        fileInfo.put("type", Files.probeContentType(file.toPath()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return fileInfo;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(fileList);
    }

    // 文件查看接口
    @GetMapping("/preview")
    public ResponseEntity<Resource> previewFile(@RequestParam String filename) throws IOException {
        Path filePath = Paths.get(FILE_DIR).resolve(filename).normalize();

        // 安全校验
        if (!filePath.startsWith(FILE_DIR)) {
            throw new SecurityException("非法文件路径");
        }

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(getContentType(filename)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    private String getContentType(String filename) throws IOException {
        String contentType = Files.probeContentType(Paths.get(filename));
        return contentType != null ? contentType : "application/octet-stream";
    }
}
