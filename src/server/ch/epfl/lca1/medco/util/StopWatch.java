package ch.epfl.lca1.medco.util;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.io.StringWriter;

/**
 * Created by misbach on 18.07.17.
 */
public class StopWatch extends org.springframework.util.StopWatch {

    // todo: get unlynx times!!
    private StopWatch(String name) {
        super(name);
    }

    /**
     * steps: time as measured
     */
    public static StopWatch
            overall = new StopWatch("Overall time"),
            steps = new StopWatch("Steps time"),
            misc = new StopWatch("Misc");
            //network = new StopWatch("Network time"),
            //db = new StopWatch("DB access time");

    /**
     * output:  stopwatch_name / task_name ; etc
     *          time_ms ; etc
     * @param additionalTimes: additional times in the JSON format that should be added to the report
     * @return
     */
    public static JsonObject generateAllCsvReports(String additionalTimes) {
        JsonObject times = Json.object();

        for (int i = 0 ; i < overall.getTaskInfo().length ; i++) {
            times.add(overall.getTaskInfo()[i].getTaskName(), overall.getTaskInfo()[i].getTimeMillis());
        }

        for (int i = 0 ; i < steps.getTaskInfo().length ; i++) {
            times.add(steps.getTaskInfo()[i].getTaskName(), steps.getTaskInfo()[i].getTimeMillis());

        }

        if (additionalTimes != null) {
            JsonObject additionalTimesParsed = Json.parse(additionalTimes).asObject();
            times.merge(additionalTimesParsed);
        }
        return times;

    }

    public static void resetTimers() {
        overall = new StopWatch("Overall time");
        steps = new StopWatch("Steps time");
        //network = new StopWatch("Network time");
        //db = new StopWatch("DB access time");
    }
}

