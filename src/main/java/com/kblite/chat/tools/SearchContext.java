package com.kblite.chat.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索来源上下文（ThreadLocal）
 * KnowledgeTools 工具执行时写入本次命中的引用来源，
 * ChatService 在工具执行完成后读取并推送 sources SSE 事件，随后清理
 *
 * @author kb-agent-lite
 */
public final class SearchContext {

    private SearchContext() {
    }

    private static final ThreadLocal<List<SourceInfo>> SOURCES = new ThreadLocal<>();

    public static void setSources(List<SourceInfo> sources) {
        SOURCES.set(sources);
    }

    /** 读取并清理 */
    public static List<SourceInfo> getAndClear() {
        List<SourceInfo> result = SOURCES.get();
        SOURCES.remove();
        return result == null ? new ArrayList<>() : result;
    }

    public static void clear() {
        SOURCES.remove();
    }

    /** 引用来源信息 */
    public record SourceInfo(String fileName, String category, double score) {
    }
}
