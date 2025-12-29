package com.aitaskmanager.service.billing;

import com.aitaskmanager.repository.customMapper.CreditPacksCustomMapper;
import com.aitaskmanager.repository.model.CreditPacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * クレジットパックに関するビジネスロジックを提供するサービス
 */
@Service
public class CreditPacksService {

    @Autowired
    private CreditPacksCustomMapper creditPacksCustomMapper;

    /**
     * 有効なクレジットパックの一覧を並び順付きで取得
     * @return 有効なクレジットパックのリスト
     */
    public List<CreditPacks> listEnabled() {
        return creditPacksCustomMapper.selectEnabledOrderBySort();
    }
}
