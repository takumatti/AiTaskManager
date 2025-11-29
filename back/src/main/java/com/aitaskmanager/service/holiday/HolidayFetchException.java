package com.aitaskmanager.service.holiday;

/**
 * 外部祝日API取得失敗を表すランタイム例外。
 */
public class HolidayFetchException extends RuntimeException {
    public HolidayFetchException(String message, Throwable cause) {
        super(message, cause);
    }
    public HolidayFetchException(String message) {
        super(message);
    }
}
