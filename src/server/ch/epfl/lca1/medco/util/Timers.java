package ch.epfl.lca1.medco.util;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.springframework.util.StopWatch;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by misbach on 18.07.17.
 */
public class Timers {

    private static Map<String, StopWatch> timers = new HashMap<>();
    private static List<String> additionalTimes = new ArrayList<>();

    public static StopWatch get(String stopWatchName) {
        if (!timers.containsKey(stopWatchName)) {
            timers.put(stopWatchName, new StopWatch(stopWatchName));
        }
        return timers.get(stopWatchName);
    }

    public static void addAdditionalTimes(String times) {
        if (times != null) {
            additionalTimes.add(times);
        }
    }

    /**
     * output:  stopwatch_name / task_name ; etc
     *          time_ms ; etc
     * @return
     */
    public static JsonObject generateFullReport() {
        JsonObject times = Json.object();

        for (String timerName : timers.keySet()) {
            JsonObject timerJson = Json.object();
            for (StopWatch.TaskInfo task : timers.get(timerName).getTaskInfo()) {
                timerJson.add(task.getTaskName(), task.getTimeMillis());
            }
            times.add(timerName, timerJson);
        }

        if (additionalTimes != null) {
            for (String additionalTime : additionalTimes) {
                JsonObject additionalTimesParsed = Json.parse(additionalTime).asObject();
                times.merge(additionalTimesParsed);
            }
        }
        return times;
    }

    public static void resetTimers() {
        timers.clear();
        additionalTimes.clear();
    }
}

