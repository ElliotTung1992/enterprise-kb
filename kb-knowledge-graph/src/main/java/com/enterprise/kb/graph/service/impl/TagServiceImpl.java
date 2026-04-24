package com.enterprise.kb.graph.service.impl;

import com.enterprise.kb.common.event.DocumentDeletedEvent;
import com.enterprise.kb.common.event.SpaceDeletedEvent;
import com.enterprise.kb.graph.service.TagService;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.common.util.SlugUtils;
import com.enterprise.kb.graph.dto.CreateTagRequest;
import com.enterprise.kb.graph.dto.TagDocumentDto;
import com.enterprise.kb.graph.dto.TagDto;
import com.enterprise.kb.graph.dto.TagTreeNode;
import com.enterprise.kb.graph.dto.UpdateTagRequest;
import com.enterprise.kb.graph.model.DocumentTag;
import com.enterprise.kb.graph.mapper.DocumentTagMapper;
import com.enterprise.kb.graph.mapper.TagMapper;
import com.enterprise.kb.graph.model.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 标签服务实现，提供标签的创建、列表、层级树查询、删除与合并功能。
 * <p>标签支持父子层级结构（通过 parentId 关联），合并标签会将源标签的文档关联迁移到目标标签。
 * 软删除标签保留数据，仅设置 deleted_at 时间戳。</p>
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final DocumentTagMapper documentTagMapper;

    /**
     * 创建标签。
     *
     * @param spaceId 空间 UUID
     * @param req     创建请求
     * @return 标签 DTO
     */
    @Override
    @Transactional
    public TagDto createTag(UUID spaceId, CreateTagRequest req) {
        String slug = req.slug() != null ? req.slug() : SlugUtils.toSlug(req.name());
        if (tagMapper.existsBySpaceIdAndSlugAndDeletedAtIsNull(spaceId, slug))
            throw new InvalidRequestException("Tag slug already exists: " + slug);
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID());
        tag.setSpaceId(spaceId);
        tag.setName(req.name());
        tag.setSlug(slug);
        tag.setColor(req.color());
        tag.setParentId(req.parentId());
        tag.setTagType(req.tagType() != null ? req.tagType() : "TAG");
        tag.setDescription(req.description());
        tag.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        tagMapper.insert(tag);
        return toDto(tag);
    }

    /**
     * 查询空间内所有标签（按 sortOrder 和 name 排序）。
     *
     * @param spaceId 空间 UUID
     * @return 标签 DTO 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<TagDto> listTags(UUID spaceId) {
        return tagMapper.findBySpaceIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(spaceId)
                .stream().map(this::toDto).toList();
    }

    /**
     * 查询标签层级树。
     *
     * @param spaceId 空间 UUID
     * @return 树形结构根节点列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<TagTreeNode> getTagTree(UUID spaceId) {
        return buildTree(tagMapper.findTagTree(spaceId));
    }

    /**
     * 更新标签基本信息。
     *
     * @param tagId 标签 UUID
     * @param req   更新请求
     * @return 更新后的标签 DTO
     */
    @Override
    @Transactional
    public TagDto updateTag(UUID tagId, UpdateTagRequest req) {
        Tag tag = tagMapper.findByIdAndDeletedAtIsNull(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
        tag.setName(req.name());
        tag.setColor(req.color());
        tag.setParentId(req.parentId());
        if (req.tagType() != null) tag.setTagType(req.tagType());
        tag.setDescription(req.description());
        if (req.sortOrder() != null) tag.setSortOrder(req.sortOrder());
        tag.setUpdatedAt(Instant.now());
        tagMapper.update(tag);
        return toDto(tag);
    }

    /**
     * 软删除标签，并清理所有文档与该标签的关联。
     *
     * @param tagId 标签 UUID
     */
    @Override
    @Transactional
    public void deleteTag(UUID tagId) {
        Tag tag = tagMapper.findByIdAndDeletedAtIsNull(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
        documentTagMapper.deleteByTagId(tagId);
        tag.setDeletedAt(Instant.now());
        tag.setUpdatedAt(Instant.now());
        tagMapper.update(tag);
    }

    /**
     * 查询标签关联的文档列表。
     *
     * @param tagId 标签 UUID
     * @return 文档摘要 DTO 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<TagDocumentDto> getTagDocuments(UUID tagId) {
        tagMapper.findByIdAndDeletedAtIsNull(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
        return documentTagMapper.findDocumentsByTagId(tagId);
    }

    /**
     * 手动添加文档标签关联。
     *
     * @param tagId      标签 UUID
     * @param documentId 文档 UUID
     * @param userId     操作用户 UUID
     */
    @Override
    @Transactional
    public void addDocumentToTag(UUID tagId, UUID documentId, UUID userId) {
        tagMapper.findByIdAndDeletedAtIsNull(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
        if (documentTagMapper.existsByDocumentIdAndTagId(documentId, tagId)) {
            throw new InvalidRequestException("Document is already associated with this tag");
        }
        DocumentTag dt = new DocumentTag();
        dt.setId(UUID.randomUUID());
        dt.setDocumentId(documentId);
        dt.setTagId(tagId);
        dt.setTaggedBy(userId);
        dt.setAutoTagged(false);
        documentTagMapper.insert(dt);
    }

    /**
     * 删除文档标签关联。
     *
     * @param tagId      标签 UUID
     * @param documentId 文档 UUID
     */
    @Override
    @Transactional
    public void removeDocumentFromTag(UUID tagId, UUID documentId) {
        documentTagMapper.deleteByDocumentIdAndTagId(documentId, tagId);
    }

    /**
     * 监听文档删除事件，清理该文档与所有标签的关联。
     *
     * @param event 文档删除事件
     */
    @EventListener
    @Transactional
    public void handleDocumentDeleted(DocumentDeletedEvent event) {
        documentTagMapper.deleteByDocumentId(event.documentId());
    }

    /**
     * 监听空间删除事件，级联清理该空间内所有标签及文档标签关联。
     *
     * @param event 空间删除事件
     */
    @EventListener
    @Transactional
    public void handleSpaceDeleted(SpaceDeletedEvent event) {
        documentTagMapper.deleteBySpaceId(event.spaceId());
        tagMapper.softDeleteBySpaceId(event.spaceId());
    }

    /**
     * 合并标签。
     * <p>将源标签的文档关联迁移到目标标签后删除源标签。</p>
     *
     * @param sourceTagId 源标签 UUID
     * @param targetTagId 目标标签 UUID
     */
    @Override
    @Transactional
    public void mergeTags(UUID sourceTagId, UUID targetTagId) {
        if (sourceTagId.equals(targetTagId))
            throw new InvalidRequestException("Cannot merge a tag with itself");
        documentTagMapper.mergeTags(sourceTagId, targetTagId);
        tagMapper.findByIdAndDeletedAtIsNull(sourceTagId).ifPresent(t -> {
            t.setDeletedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            tagMapper.update(t);
        });
    }

    private List<TagTreeNode> buildTree(List<Tag> tags) {
        Map<String, TagTreeNode> nodeMap = new LinkedHashMap<>();
        List<TagTreeNode> roots = new ArrayList<>();
        for (Tag tag : tags) {
            TagTreeNode node = new TagTreeNode(tag.getId(), tag.getName(), tag.getSlug(),
                    tag.getTagType(), tag.getColor(), tag.getParentId(), 0);
            nodeMap.put(tag.getId().toString(), node);
            if (tag.getParentId() == null) {
                roots.add(node);
            } else {
                TagTreeNode parent = nodeMap.get(tag.getParentId().toString());
                if (parent != null) parent.children().add(node);
            }
        }
        return roots;
    }

    private TagDto toDto(Tag t) {
        return new TagDto(t.getId(), t.getSpaceId(), t.getName(), t.getSlug(),
                t.getColor(), t.getParentId(), t.getTagType(), t.getDescription(),
                t.getSortOrder(), t.getCreatedAt());
    }
}
