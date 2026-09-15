package com.kblite.knowledge.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kblite.knowledge.entity.KnowledgeDocument;
import lombok.Data;

import java.text.DecimalFormat;
import java.util.Date;

/**
 * 文档列表 VO
 *
 * @author kb-agent-lite
 */
@Data
public class DocumentVO {

    private Long id;
    private String documentId;
    private String title;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private String fileSizeFormatted;
    private String fileExtension;
    private String categoryId;
    private String categoryName;
    private Integer vectorStatus;
    private String vectorStatusText;
    private Integer vectorCount;
    private Integer chunkCount;
    private String vectorError;
    private String embeddingModel;
    private String tags;
    private String remark;
    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public static DocumentVO from(KnowledgeDocument doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setDocumentId(doc.getDocumentId());
        vo.setTitle(doc.getTitle());
        vo.setOriginalFileName(doc.getOriginalFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setFileSizeFormatted(formatFileSize(doc.getFileSize()));
        vo.setFileExtension(doc.getFileExtension());
        vo.setCategoryId(doc.getCategoryId());
        vo.setCategoryName(doc.getCategoryName());
        vo.setVectorStatus(doc.getVectorStatus());
        vo.setVectorStatusText(vectorStatusText(doc.getVectorStatus()));
        vo.setVectorCount(doc.getVectorCount());
        vo.setChunkCount(doc.getChunkCount());
        vo.setVectorError(doc.getVectorError());
        vo.setEmbeddingModel(doc.getEmbeddingModel());
        vo.setTags(doc.getTags());
        vo.setRemark(doc.getRemark());
        vo.setCreateUser(doc.getCreateUser());
        vo.setCreateTime(doc.getCreateTime());
        return vo;
    }

    private static String formatFileSize(Long size) {
        if (size == null || size == 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double fileSize = size.doubleValue();
        while (fileSize >= 1024 && unitIndex < units.length - 1) {
            fileSize /= 1024;
            unitIndex++;
        }
        return new DecimalFormat("#.##").format(fileSize) + " " + units[unitIndex];
    }

    private static String vectorStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "处理中";
            case 2 -> "成功";
            case 3 -> "失败";
            default -> "未知";
        };
    }
}
