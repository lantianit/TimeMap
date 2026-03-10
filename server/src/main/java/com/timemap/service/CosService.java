package com.timemap.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.timemap.config.CosConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosService {

    private final COSClient cosClient;
    private final CosConfig cosConfig;

    public void deleteByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            String host = cosConfig.getBucket() + ".cos." + cosConfig.getRegion() + ".myqcloud.com/";
            int idx = fileUrl.indexOf(host);
            if (idx < 0) {
                log.warn("COS URL 格式不匹配，跳过删除: {}", fileUrl);
                return;
            }
            String key = fileUrl.substring(idx + host.length());
            cosClient.deleteObject(cosConfig.getBucket(), key);
            log.info("COS 删除成功: {}", key);
        } catch (Exception e) {
            log.error("COS 删除失败: {}", fileUrl, e);
        }
    }

    /**
     * 上传文件到 COS，返回访问 URL
     */
    public String upload(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String ext = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".png";

            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String key = "photos/" + datePath + "/" + UUID.randomUUID() + ext;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest putRequest = new PutObjectRequest(
                    cosConfig.getBucket(), key, file.getInputStream(), metadata);
            cosClient.putObject(putRequest);

            String url = "https://" + cosConfig.getBucket() + ".cos." + cosConfig.getRegion() + ".myqcloud.com/" + key;
            log.info("COS 上传成功: {}", url);
            return url;
        } catch (Exception e) {
            log.error("COS 上传失败", e);
            throw new RuntimeException("图片上传失败: " + e.getMessage());
        }
    }
}
