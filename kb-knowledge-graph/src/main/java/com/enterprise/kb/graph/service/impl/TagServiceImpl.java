package com.enterprise.kb.graph.service.impl;

import com.enterprise.kb.graph.service.TagService;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.common.util.SlugUtils;
import com.enterprise.kb.graph.dto.CreateTagRequest;
import com.enterprise.kb.graph.dto.TagDto;
import com.enterprise.kb.graph.dto.TagTreeNode;
import com.enterprise.kb.graph.mapper.DocumentTagMapper;
import com.enterprise.kb.graph.mapper.TagMapper;
import com.enterprise.kb.graph.model.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 软删除标签。
     *
     * @param tagId 标签 UUID
     */
    @Override
    @Transactional
    public void deleteTag(UUID tagId) {
        Tag tag = tagMapper.findByIdAndDeletedAtIsNull(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
        tag.setDeletedAt(Instant.now());
        tag.setUpdatedAt(Instant.now());
        tagMapper.update(tag);
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
