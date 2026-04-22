package com.enterprise.kb.graph.service;

import com.enterprise.kb.graph.dto.CreateTagRequest;
import com.enterprise.kb.graph.dto.TagDto;
import com.enterprise.kb.graph.dto.TagTreeNode;

import java.util.List;
import java.util.UUID;

/**
 * Tag management service for creating, listing, and managing document tags.
 */
public interface TagService {

    /**
     * Create a new tag in a space.
     *
     * @param spaceId the space ID
     * @param req     the tag creation request
     * @return the created tag DTO
     */
    TagDto createTag(UUID spaceId, CreateTagRequest req);

    /**
     * List all non-deleted tags in a space.
     *
     * @param spaceId the space ID
     * @return list of tag DTOs ordered by sort order and name
     */
    List<TagDto> listTags(UUID spaceId);

    /**
     * Build a hierarchical tree of tags in a space.
     *
     * @param spaceId the space ID
     * @return list of root tag tree nodes with nested children
     */
    List<TagTreeNode> getTagTree(UUID spaceId);

    /**
     * Soft-delete a tag.
     *
     * @param tagId the tag ID to delete
     */
    void deleteTag(UUID tagId);

    /**
     * Merge a source tag into a target tag, updating all document associations.
     *
     * @param sourceTagId the source tag to merge from
     * @param targetTagId the target tag to merge into
     */
    void mergeTags(UUID sourceTagId, UUID targetTagId);
}
