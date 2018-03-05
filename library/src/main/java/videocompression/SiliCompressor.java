package videocompression;


import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.net.URISyntaxException;

import utils.FileUtils;

/**
 * @author Toure, Akah L
 * @version 1.1.1
 *          Created by Toure on 28/03/2016.
 */
public class SiliCompressor {

    private static final String LOG_TAG = SiliCompressor.class.getSimpleName();
    public static String videoCompressionPath;

    static volatile SiliCompressor singleton = null;
    private static Context mContext;

    public SiliCompressor(Context context) {
        mContext = context;
    }

    // initialise the class and set the context
    public static SiliCompressor with(Context context) {
        if (singleton == null) {
            synchronized (SiliCompressor.class) {
                if (singleton == null) {
                    singleton = new Builder(context).build();
                }
            }
        }
        return singleton;

    }

    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     * This uses default values for the converted videos
     * @param videoFileUri source uri for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @return The Path of the compressed video file
     */
    public String compressVideo(Uri videoFileUri, String destinationDir) throws URISyntaxException {
        return compressVideo( videoFileUri, destinationDir, 0, 0, 0 );
    }

    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     *
     * @param videoFileUri   source uri for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @param outWidth       the target width of the compressed video or 0 to use default width
     * @param outHeight      the target height of the compressed video or 0 to use default height
     * @param bitrate        the target bitrate of the compressed video or 0 to user default bitrate
     * @return The Path of the compressed video file
     */
    public String compressVideo(Uri videoFileUri, String destinationDir, int outWidth, int outHeight, int bitrate) throws URISyntaxException {
        boolean isconverted = MediaController.getInstance().convertVideo(FileUtils.getPath(mContext, videoFileUri), new File(destinationDir), outWidth, outHeight, bitrate);
        if (isconverted) {
            Log.v(LOG_TAG, "Video Conversion Complete");
        } else {
            Log.v(LOG_TAG, "Video conversion in progress");
        }

        return MediaController.cachedFile.getPath();

    }

    /**
     * Fluent API for creating {@link SiliCompressor} instances.
     */
    public static class Builder {

        private final Context context;


        /**
         * Start building a new {@link SiliCompressor} instance.
         */
        public Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            }
            this.context = context.getApplicationContext();
        }


        /**
         * Create the {@link SiliCompressor} instance.
         */
        public SiliCompressor build() {
            Context context = this.context;

            return new SiliCompressor(context);
        }
    }
}