package com.aitaskmanager.service.holiday;

import java.time.LocalDate;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aitaskmanager.repository.dto.holiday.PublicHoliday;

/**
 * 年末に翌年分の祝日をプリフェッチしキャッシュを温めるスケジューラ。
 */
@Component
public class HolidayPrefetchScheduler {

    private static final Logger log = LogManager.getLogger(HolidayPrefetchScheduler.class);
    private final HolidayService holidayService;

    /**
     * コンストラクタ
     * 
     * @param holidayService 祝日サービス
     */
    public HolidayPrefetchScheduler(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    /**
     * 毎日 03:10 に実行。12月のみ翌年分を forceRefresh。
     * Cron: 秒 分 時 日 月 曜日
     */
    @Scheduled(cron = "0 10 3 * 12 *")
    public void prefetchNextYear() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int nextYear = year + 1;
        // 12月だけプリフェッチ
        if (today.getMonthValue() == 12) {
            log.info("[HolidayPrefetch] Prefetching holidays for {}", nextYear);
            List<PublicHoliday> list = holidayService.forceRefresh(nextYear);
            log.info("[HolidayPrefetch] Prefetched {} holidays for {}", list.size(), nextYear);
        }
    }
}
