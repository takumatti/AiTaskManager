package com.aitaskmanager.controller.billing;

import com.aitaskmanager.repository.model.CreditPacks;
import com.aitaskmanager.service.billing.CreditPacksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * クレジットパックに関連するAPIエンドポイントを提供するコントローラー
 */
@RestController
@RequestMapping("/api/credit-packs")
public class CreditPacksController {

    @Autowired
    private CreditPacksService creditPacksService;

    /**
     * 有効なクレジットパックの一覧を取得するエンドポイント
     *
     * @return クレジットパックのリスト
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CreditPacks>> listEnabled() {
        List<CreditPacks> packs = creditPacksService.listEnabled();
        return ResponseEntity.ok(packs);
    }
}
