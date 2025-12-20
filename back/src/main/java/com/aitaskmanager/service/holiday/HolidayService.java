package com.aitaskmanager.service.holiday;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.aitaskmanager.repository.dto.holiday.PublicHoliday;

/**
 * Nager.Date API から祝日を取得しキャッシュするサービス。
 */
@Service
public class HolidayService {
    private static final Logger log = LogManager.getLogger(HolidayService.class);

    private static final String ENDPOINT = "https://date.nager.at/api/v3/PublicHolidays/%d/JP";
    private final RestTemplate restTemplate;

    /** キャッシュエントリ */
    private static class CacheEntry {
        final List<PublicHoliday> holidays;
        final Instant fetchedAt;
        CacheEntry(List<PublicHoliday> holidays) {
            this.holidays = holidays;
            this.fetchedAt = Instant.now();
        }
    }
    private final Map<Integer, CacheEntry> cache = new ConcurrentHashMap<>();

    // TTL 設定（12時間）
    private static final Duration TTL = Duration.ofHours(12);

    /** コンストラクタ */
    public HolidayService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000); // 2s
        f.setReadTimeout(3000);    // 3s
        this.restTemplate = new RestTemplate(f);
    }

    /**
     * 指定された年の祝日リストを取得する
     * キャッシュが有効な場合はキャッシュを返し、無効な場合は API から再取得する
     * 
     * @param year 取得する祝日の年
     * @return 祝日のリスト
     * @throws HolidayFetchException 祝日取得に失敗した場合
     */
    public List<PublicHoliday> getHolidays(int year) {
        // キャッシュ有効性判定
        CacheEntry entry = cache.get(year);
        if (entry != null && Instant.now().isBefore(entry.fetchedAt.plus(TTL))) {
            log.debug("Holiday cache hit year={} size={}", year, entry.holidays.size());
            return entry.holidays;
        }
        // API取得
        try {
            long start = System.currentTimeMillis();
            String url = String.format(ENDPOINT, year);
            PublicHoliday[] arr = restTemplate.getForObject(url, PublicHoliday[].class);
            List<PublicHoliday> list = arr == null ? Collections.emptyList() : List.of(arr);
            cache.put(year, new CacheEntry(list));
            log.info("Holiday fetch year={} size={} took={}ms", year, list.size(), System.currentTimeMillis()-start);
            return list;
        } catch (Exception ex) {
            // 失敗時: 古いキャッシュがあればそれを返す、なければ空
            if (entry != null) {
                log.warn("Holiday fetch failed year={} using stale cache size={} cause={}", year, entry.holidays.size(), ex.getMessage());
                return entry.holidays;
            }
            log.error("Holiday fetch failed year={} no cache cause={}", year, ex.getMessage());
            throw new HolidayFetchException("外部祝日API取得失敗", ex);
        }
    }

    /**
     * TTL を待たずに再取得（プリフェッチ用）
     * 
     * @param year 取得する祝日の年
     * @return 祝日のリスト
     */
    public List<PublicHoliday> forceRefresh(int year) {
        try {
            long start = System.currentTimeMillis();
            String url = String.format(ENDPOINT, year);
            PublicHoliday[] arr = restTemplate.getForObject(url, PublicHoliday[].class);
            List<PublicHoliday> list = arr == null ? Collections.emptyList() : List.of(arr);
            cache.put(year, new CacheEntry(list));
            log.info("Holiday forceRefresh year={} size={} took={}ms", year, list.size(), System.currentTimeMillis()-start);
            return list;
        } catch (Exception ex) {
            // 失敗時は現状キャッシュを保持
            CacheEntry entry = cache.get(year);
            log.warn("Holiday forceRefresh failed year={} returning cached size={} cause={}", year, entry != null ? entry.holidays.size() : 0, ex.getMessage());
            return entry != null ? entry.holidays : Collections.emptyList();
        }
    }
}
