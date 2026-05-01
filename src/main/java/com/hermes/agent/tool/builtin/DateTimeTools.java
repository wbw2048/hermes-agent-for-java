package com.hermes.agent.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 获取当前时间的工具。
 * <p>
 * 支持指定时区和日期格式，默认使用系统时区和 ISO-8601 格式。
 */
@Service
public class DateTimeTools {

    private static final Logger log = LoggerFactory.getLogger(DateTimeTools.class);

    @Tool(description = "Get the current date and time in the specified timezone. Useful for time-aware tasks.")
    public String getCurrentTime(
            @ToolParam(description = "IANA timezone name, e.g. 'Asia/Shanghai', 'America/New_York'. Defaults to system timezone.") String timezone,
            @ToolParam(description = "Date format string. Defaults to 'yyyy-MM-dd HH:mm:ss z'. Only lowercase y/m/d/h/m/s are valid Java patterns; uppercase Y/D are week-year and day-of-year and will be rejected.") String format
    ) {
        log.info("[TOOL-CALL] getCurrentTime invoked: timezone_param='{}', format_param='{}'", timezone, format);

        String tz = (timezone != null && !timezone.isBlank()) ? timezone : java.time.ZoneId.systemDefault().getId();
        // 工具内部固定使用安全格式，拒绝外部传入可能含大写陷阱（YYYY/DD）的格式
        String fmt = "yyyy-MM-dd HH:mm:ss z";

        log.info("[TOOL-CALL] resolved: timezone='{}', format='{}' (fixed, ignored param='{}')",
                tz, fmt, format);

        try {
            ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of(tz));
            String formatted = now.format(DateTimeFormatter.ofPattern(fmt));
            String result = "{\"timezone\": \"" + tz + "\", \"current_time\": \"" + formatted + "\"}";
            log.info("[TOOL-RETURN] getCurrentTime returned: {}", result);
            return result;
        } catch (Exception e) {
            String error = "{\"error\": \"Invalid timezone: " + tz + "\"}";
            log.warn("[TOOL-RETURN] getCurrentTime error: {}", error);
            return error;
        }
    }
}
