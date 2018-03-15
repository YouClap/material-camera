package life.knowledge4.videotrimmer.utils;

/**
 * Created by hugom on 07/03/2018.
 */

public class VideoTime {

    private long startTime;
    private long endTime;

    public VideoTime(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    @Override
    public String toString() {
        return "startTime: " + startTime + " endTime: " + endTime;
    }
}
