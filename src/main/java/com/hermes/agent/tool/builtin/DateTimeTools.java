package com.hermes.agent.tool.builtin;

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

    @Tool(description = "Get the current date and time in the specified timezone. Useful for time-aware tasks.")
    public String getCurrentTime(
            @ToolParam(description = "IANA timezone name, e.g. 'Asia/Shanghai', 'America/New_York'. Defaults to system timezone.") String timezone,
            @ToolParam(description = "Date format string. Defaults to ISO-8601.") String format
    ) {
        String tz = (timezone != null && !timezone.isBlank()) ? timezone : java.time.ZoneId.systemDefault().getId();
        String fmt = (format != null && !format.isBlank()) ? format : "yyyy-MM-dd HH:mm:ss z";

        try {
            ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of(tz));
            String formatted = now.format(DateTimeFormatter.ofPattern(fmt));
            return "{\"timezone\": \"" + tz + "\", \"current_time\": \"" + formatted + "\"}";
        } catch (Exception e) {
            return "{\"error\": \"Invalid timezone: " + tz + "\"}";
        }
    }
}
