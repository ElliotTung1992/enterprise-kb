package com.enterprise.kb.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 提交售后审核申请工具的输入参数，由 LLM 整理用户诉求后调用。
 *
 * @param orderId          订单号
 * @param reason           售后原因
 * @param orderDetailsJson 订单详情 JSON
 */
public record AfterSalesSubmitInput(
        @JsonProperty(value = "orderId", required = true) String orderId,
        @JsonProperty(value = "reason", required = true) String reason,
        @JsonProperty(value = "orderDetailsJson", required = false) String orderDetailsJson
) {}
