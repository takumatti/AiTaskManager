package com.aitaskmanager.repository.dto.subscription;

import lombok.Data;

/**
 * プラン変更のリクエストDTO
 */
@Data
public class ChangePlanRequest {
    /** 新しいプランID */
    private Integer planId;
}
