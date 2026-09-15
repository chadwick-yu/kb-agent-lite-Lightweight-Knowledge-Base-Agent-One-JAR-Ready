package com.kblite.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 知识库分类实体（对应表 knowledge_category）
 *
 * @author kb-agent-lite
 */
@Data
@TableName("knowledge_category")
public class KnowledgeCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类ID，0表示顶级 */
    private Long parentId;

    private String categoryName;

    /** 分类编码（唯一） */
    private String categoryCode;

    private String description;

    private Integer sortOrder;

    private Integer level;

    /** 分类路径（如 0/1/5） */
    private String path;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    private Integer documentCount;

    private String createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private String updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 子分类（非数据库字段） */
    @TableField(exist = false)
    private List<KnowledgeCategory> children;
}
