package com.enterprise.kb.document.service;

/**
 * Markdown 图片理解服务。
 */
public interface MdImageUnderstandingService {

    /**
     * 对 Markdown 图片执行视觉理解。
     *
     * @param image 图片输入
     * @return 图片理解结果
     */
    MdImageUnderstandingResult understand(MdImageInput image);
}

