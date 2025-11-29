package com.aitaskmanager.repository.dto.holiday;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Nager.Date API レスポンスの祝日DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class PublicHoliday {
    /** 祝日の日付 (YYYY-MM-DD) */
    private String date;
    /** 祝日の名称 */
    private String localName;
    /** 祝日の英語名称 */
    private String name;
    /** 国コード */
    private String countryCode;
    /** 固定祝日かどうか */
    private boolean fixed;
    /** グローバル祝日かどうか */
    private boolean global;
    /** 祝日開始年 */
    private Integer launchYear;
    /** 祝日タイプ */
    private String type;
}
