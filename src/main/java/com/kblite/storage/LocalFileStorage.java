package com.kblite.storage;

import com.kblite.common.BizException;
import com.kblite.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘文件存储（替代 MinIO）
 * 存储路径：{dataDir}/files/{category}/{yyyy}/{MM}/{uuid}.{ext}
 *
 * @author kb-agent-lite
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorage {

    private static final DateTimeFormatter YEAR_FMT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MM");

    private final AppProperties appProperties;

    /**
     * 保存上传文件，返回相对路径（category/yyyy/MM/uuid.ext）
     */
    public String save(MultipartFile file, String category) {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1);
        }
        LocalDate today = LocalDate.now();
        String relativePath = (category == null || category.isBlank() ? "KNOWLEDGE" : sanitize(category))
                + "/" + today.format(YEAR_FMT) + "/" + today.format(MONTH_FMT)
                + "/" + UUID.randomUUID().toString().replace("-", "")
                + (ext.isEmpty() ? "" : "." + ext);
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[FileStorage] 文件已保存: {}, 大小: {} bytes", relativePath, file.getSize());
            return relativePath;
        } catch (IOException e) {
            throw new BizException("文件保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 打开文件流
     */
    public InputStream open(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.exists(path)) {
            throw new BizException("文件不存在: " + relativePath);
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件（不存在时静默忽略）
     */
    public boolean delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        try {
            return Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.error("[FileStorage] 文件删除失败: {}", relativePath, e);
            return false;
        }
    }

    /** 分类目录名只允许中文/字母数字/-_，防目录穿越 */
    private String sanitize(String category) {
        return category.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "");
    }

    private Path resolve(String relativePath) {
        Path base = Paths.get(appProperties.getDataDir()).toAbsolutePath().normalize();
        Path target = base.resolve("files").resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new BizException("非法文件路径");
        }
        return target;
    }
}
