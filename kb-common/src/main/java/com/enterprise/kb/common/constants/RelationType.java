package com.enterprise.kb.common.constants;

/**
 * 文档关系类型。
 */
public enum RelationType {
    /** 当前文档引用了目标文档。 */
    REFERENCES,
    /** 当前文档取代了目标文档。 */
    SUPERSEDES,
    /** 当前文档与目标文档存在泛关联关系。 */
    RELATED_TO,
    /** 当前文档派生自目标文档。 */
    DERIVED_FROM
}
