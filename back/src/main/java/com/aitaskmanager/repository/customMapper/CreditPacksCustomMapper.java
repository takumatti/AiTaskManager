package com.aitaskmanager.repository.customMapper;

import com.aitaskmanager.repository.model.CreditPacks;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CreditPacksテーブルに対するカスタムマッパー
 */
@Mapper
public interface CreditPacksCustomMapper {

    /**
     * Stripe Price IDでクレジットパック取得
     *
     * @param stripePriceId Stripe Price ID
     * @return クレジットパック情報
     */
    CreditPacks selectByStripePriceId(@Param("stripePriceId") String stripePriceId);

    /**
     * 有効なクレジットパックを並び順で取得
     *
     * @return クレジットパックのリスト
     */
    List<CreditPacks> selectEnabledOrderBySort();
}
