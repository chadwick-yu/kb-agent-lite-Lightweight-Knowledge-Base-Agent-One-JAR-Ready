package com.kblite.knowledge.model;

import lombok.Getter;

/**
 * 支持的文件类型白名单
 *
 * @author kb-agent-lite
 */
@Getter
public enum SupportedFileType {
    PDF("application/pdf", "pdf"),
    DOC("application/msword", "doc"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    XLS("application/vnd.ms-excel", "xls"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PPT("application/vnd.ms-powerpoint", "ppt"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
    MD("text/markdown", "md"),
    MD_X("text/x-markdown", "md"),
    MD_WEB("text/x-web-markdown", "md"),
    TXT("text/plain", "txt"),
    RTF("application/rtf", "rtf"),
    RTF_TEXT("text/rtf", "rtf");

    private final String mimeType;
    private final String extension;

    SupportedFileType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static boolean isSupported(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        for (SupportedFileType type : values()) {
            if (type.getMimeType().equals(mimeType)) {
                return true;
            }
        }
        return false;
    }
}
