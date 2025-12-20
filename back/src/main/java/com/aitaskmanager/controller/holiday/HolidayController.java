package com.aitaskmanager.controller.holiday;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitaskmanager.repository.dto.holiday.PublicHoliday;
import com.aitaskmanager.service.holiday.HolidayService;
import com.aitaskmanager.service.holiday.HolidayFetchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.aitaskmanager.util.LogUtil;

/**
 * 祝日取得APIコントローラ
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    @Autowired
    private HolidayService holidayService;

    /**
     * 指定された年の祝日リストを取得するエンドポイント
     *
     * @param year 取得する祝日の年
     * @return 祝日のリスト
     */
    @GetMapping
    public List<PublicHoliday> get(@RequestParam(name = "year", required = true) Integer year) {
        LogUtil.controller(HolidayController.class, "holidays.list", null, null, "invoked");
        if (year == null || year < 1900 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year parameter is invalid");
        }
        try {
            return holidayService.getHolidays(year);
        } catch (HolidayFetchException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }
}
