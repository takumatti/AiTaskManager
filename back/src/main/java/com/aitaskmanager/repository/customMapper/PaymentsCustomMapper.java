package com.aitaskmanager.repository.customMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Paymentsテーブルに対するカスタムマッパー
 */
@Mapper
public interface PaymentsCustomMapper {

    /**
     * 支払いIDで支払い情報の存在を確認する
     * 
     * @param paymentId 支払いID
     * @return 存在する場合は1、存在しない場合は0
     */
    int existsByPaymentId(@Param("paymentId") String paymentId);

    /**
     * 支払い情報を挿入する
     * 
     * @param userSid ユーザーSID
     * @param amount 金額
     * @param currency 通貨
     * @param paymentMethod 支払い方法
     * @param paymentId 支払いID
     * @param status 支払いステータス
     * @return 挿入された行数
     */
    int insertPayment(@Param("userSid") int userSid,
                      @Param("amount") double amount,
                      @Param("currency") String currency,
                      @Param("paymentMethod") String paymentMethod,
                      @Param("paymentId") String paymentId,
                      @Param("status") String status);
}