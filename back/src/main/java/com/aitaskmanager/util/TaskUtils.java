package com.aitaskmanager.util;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * タスク関連のユーティリティクラス
 */
public final class TaskUtils {

    /**
     * nullならデフォルト文字列に変換
     * 
     * @param value 元の文字列
     * @param defaultVal デフォルト文字列
     * @return 変換後の文字列
     */
    public static String defaultString(String value, String defaultVal) {
        return value != null ? value : defaultVal;
    }

    /**
     * priority値の正規化（デフォルト値はNORMAL）
     * 
     * @param priority 元のpriority値
     * @return 正規化されたpriority値
     */
    public static String normalizePriority(String priority) {
        if (priority == null) return "NORMAL";
        switch (priority) {
            case "HIGH":
            case "NORMAL":
            case "LOW":
                return priority;
            default:
                return "NORMAL";
        }
    }

    /**
     * status値の正規化（デフォルト値はTODO）
     * 
     * @param status 元のstatus値
     * @return 正規化されたstatus値
     */
    public static String normalizeStatus(String status) {
        if (status == null) return "TODO";
        switch (status) {
            case "TODO":
            case "DOING":
            case "DONE":
                return status;
            default:
                return "TODO";
        }
    }

    /**
     * 日付文字列をjava.sql.Dateに変換（yyyy/MM/dd または yyyy-MM-dd を許容）
     * 
     * @param s 日付文字列
     * @return java.sql.Date
     */
    public static Date toSqlDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Date.valueOf(LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy/MM/dd")));
        } catch (Exception ignore) {
            return Date.valueOf(LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
    }
}
