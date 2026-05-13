package com.enterprise.kb.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 售后资格检查工具的输入参数，由 LLM 提供用户的订单号。
 */
public record AfterSalesCheckInput(
        @JsonProperty(value = "orderId", required = true) String orderId
) {}
