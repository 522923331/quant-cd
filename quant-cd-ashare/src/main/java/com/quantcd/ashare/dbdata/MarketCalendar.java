package com.quantcd.ashare.dbdata;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarketCalendar {

    private MarketCalendar() {
    }

    public static int lastCompletedYear(LocalDate today) {
        return today.getYear() - 1;
    }

    public static int priceStartYear(LocalDate today) {
        return today.getYear() - 11;
    }

    public static List<Integer> screenYears(LocalDate today, int n) {
        int end = lastCompletedYear(today);
        List<Integer> years = new ArrayList<Integer>();
        for (int year = end - n + 1; year <= end; year++) {
            years.add(year);
        }
        return years;
    }

    public static List<String> yearEndSampleDates(List<String> openDates, int fromYear, int toYear) {
        Map<Integer, List<String>> byYear = new HashMap<Integer, List<String>>();
        for (String date : openDates) {
            if (date == null || date.length() < 4) {
                continue;
            }
            int year = Integer.parseInt(date.substring(0, 4));
            if (year < fromYear || year > toYear) {
                continue;
            }
            List<String> bucket = byYear.get(year);
            if (bucket == null) {
                bucket = new ArrayList<String>();
                byYear.put(year, bucket);
            }
            bucket.add(date);
        }
        List<String> picked = new ArrayList<String>();
        for (int year = fromYear; year <= toYear; year++) {
            List<String> bucket = byYear.get(year);
            if (bucket == null) {
                continue;
            }
            Collections.sort(bucket);
            int from = Math.max(0, bucket.size() - 5);
            picked.addAll(bucket.subList(from, bucket.size()));
        }
        return picked;
    }
}
