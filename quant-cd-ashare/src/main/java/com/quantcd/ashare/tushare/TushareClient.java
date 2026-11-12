package com.quantcd.ashare.tushare;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface TushareClient {

    List<Map<String, String>> query(String apiName, Map<String, String> params, String fields);
}
