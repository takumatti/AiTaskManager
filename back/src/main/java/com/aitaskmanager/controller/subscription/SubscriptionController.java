package com.aitaskmanager.controller.subscription;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitaskmanager.service.subscription.SubscriptionService;
import com.aitaskmanager.util.LogUtil;

import lombok.RequiredArgsConstructor;

/**
 * サブスクリプションおよびプランの参照系APIエンドポイントを提供するコントローラー
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {

	@Autowired
	private SubscriptionService subscriptionService;

	/**
	 * 利用可能なサブスクリプションプランのリストを取得
     * 
     * @return プランのリスト
	 */
	@GetMapping("/plans")
	public ResponseEntity<List<Map<String, Object>>> getPlans() {
		LogUtil.controller(SubscriptionController.class, "plans.list", null, null, "invoked");
		return ResponseEntity.ok(subscriptionService.getPlans());
	}
}
