package com.music.android.uamp.utils;

import org.jsoup.nodes.Document;

/**
 * Created by sagar on 29/12/16.
 */

public class ParserHelper {
    public static long getDuration(String dur) {
        String time = dur.substring(2);
        long duration = 0L;
        Object[][] indexs = new Object[][]{{"H", 3600},{"h", 3600}, {"M", 60}, {"m", 60}, {"S", 1},{"s", 1}};
        for (int i = 0; i < indexs.length; i++) {
            int index = time.indexOf((String) indexs[i][0]);
            if (index != -1) {
                String value = time.substring(0, index);
                duration += Integer.parseInt(value) * (int) indexs[i][1] * 1000;
                time = time.substring(value.length() + 1);
            }
        }
        return duration;
    }

    public static long getDurationfromString(String time) {
        long duration = 0L;
        Object[][] indexs = new Object[][]{{"H", 3600},{"h", 3600}, {"M", 60}, {"m", 60}, {"S", 1},{"s", 1}};
        for (int i = 0; i < indexs.length; i++) {
            int index = time.indexOf((String) indexs[i][0]);
            if (index != -1) {
                String value = time.substring(0, index);
                duration += Double.parseDouble(value) * (int) indexs[i][1] * 1000;
                time = time.substring(value.length() + 1);
            }
        }
        return duration;
    }
}
