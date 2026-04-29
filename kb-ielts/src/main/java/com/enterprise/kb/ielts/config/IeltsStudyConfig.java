package com.enterprise.kb.ielts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 雅思每日学习配置（从 application.yml 注入）
 */
@Component
@ConfigurationProperties(prefix = "enterprise.ielts.study")
@Getter
@Setter
public class IeltsStudyConfig {

    /** 每日新增单词数量 */
    private int dailyWords = 20;
    /** 每日新增短语数量 */
    private int dailyPhrases = 10;
    /** 每日新增语法数量 */
    private int dailyGrammar = 5;
    /** 每日其他类型内容数量 */
    private int dailyOthers = 5;
}
