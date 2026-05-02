package com.hermes.agent.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hermes.agent.tool.annotation.ToolSet;
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
@ToolSet("datetime")
public class DateTimeTools {

    private static final Logger log = LoggerFactory.getLogger(DateTimeTools.class);

    @Tool(description = "获取当前日期和时间。当用户询问当前时间、几点、今天日期、星期几、日期等与时间相关的问题时，必须调用此工具。如果用户未指定时区，默认使用系统时区。")
    public String getCurrentTime(
            @ToolParam(description = "IANA 时区名称，例如 'Asia/Shanghai'、'America/New_York'。默认使用系统时区。") String timezone,
            @ToolParam(description = "日期格式字符串。默认 'yyyy-MM-dd HH:mm:ss z'。") String format
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
