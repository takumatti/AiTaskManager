package com.aitaskmanager.controller.subscription.dto;

import lombok.Data;

/**
 * プラン変更のリクエストDTO
 */
@Data
public class ChangePlanRequest {
    /** 新しいプランID */
    private Integer planId;
}
