package videocompression;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import id.zelory.compressor.Compressor;

/**
 * @author Toure, Akah L
 * @version 1.1.1
 * Created by Toure on 28/03/2016.
 */
public class SiliCompressor {

    private static final String LOG_TAG = SiliCompressor.class.getSimpleName();

    private static final int DEFAULT_IMAGE_WIDTH = 640;
    private static final int DEFAULT_IMAGE_HEIGHT = 480;
    private static final int DEFAULT_IMAGE_QUALITY = 75;

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
     * Compresses the image at the specified Uri String and and return the filepath of the compressed image.
     *
     * @param imageFilePath      image file path String of the source image you wish to compress
     * @param destinationDirPath destination directory path string where the compressed image will be saved
     * @return compressed Image file path String
     */
    public String compressImage(String imageFilePath, String destinationDirPath) {
        return compressImage(imageFilePath, destinationDirPath, DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT, DEFAULT_IMAGE_QUALITY, false);
    }

    /**
     * Compresses the image at the specified Uri String and and return the filepath of the compressed image.
     * TODO improve fix issue - the image file path can't be child of the destination directory path. If not Compressor object will break.
     *
     * @param imageFilePath      image file path String of the source image you wish to compress
     * @param destinationDirPath destination directory path string where the compressed image will be saved
     * @param outWidth           the target width of the compressed image or 0 to use default width
     * @param outHeight          the target height of the compressed image or 0 to use default height
     * @param quality            the target quality compression. ex: 100 = loseless compression
     * @param deleteSourceImage  boolean value to flag if want to delete the source file after compression
     * @return compressed Image file path String
     */
    public String compressImage(String imageFilePath, String destinationDirPath, int outWidth, int outHeight, int quality, boolean deleteSourceImage) {
        if (imageFilePath.startsWith(destinationDirPath))
            return "";

        File compressedImageFile = null;
        try {
            compressedImageFile = new Compressor(mContext)
                    .setMaxWidth(outWidth)
                    .setMaxHeight(outHeight)
                    .setQuality(quality)
                    .setCompressFormat(Bitmap.CompressFormat.WEBP)
                    .setDestinationDirectoryPath(destinationDirPath)
                    .compressToFile(new File(imageFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (deleteSourceImage) {
            File source = new File(imageFilePath);
            if (source.exists()) {
                boolean isdeleted = source.delete();
                Log.d(LOG_TAG, (isdeleted) ? "SourceImage File deleted" : "SourceImage File not deleted");
            }
        }
        return compressedImageFile.getAbsolutePath();
    }


    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     * This uses default values for the converted videos
     *
     * @param videoFilePath  source path for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @return The Path of the compressed video file
     */
    public String compressVideo(String videoFilePath, String destinationDir) {
        return compressVideo(videoFilePath, destinationDir, -1l, -1l, 0, 0, 0);
    }

    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     * This uses default values for the converted videos
     *
     * @param videoFileUri   source uri for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @return The Path of the compressed video file
     */
    public String compressVideo(Uri videoFileUri, String destinationDir) throws URISyntaxException {
        return compressVideo(videoFileUri, destinationDir, -1l, -1l, 0, 0, 0);
    }

    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     *
     * @param videoFileUri   source uri for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @param startTime      start compression from a start time video (in microseconds) - from trimming purpose only
     * @param endTime        end the compression from a end time video (in microseconds) - from trimming purpose only
     * @param outWidth       the target width of the compressed video or 0 to use default width
     * @param outHeight      the target height of the compressed video or 0 to use default height
     * @param bitrate        the target bitrate of the compressed video or 0 to user default bitrate
     * @return The Path of the compressed video file
     */
    public String compressVideo(Uri videoFileUri, String destinationDir, long startTime, long endTime, int outWidth, int outHeight, int bitrate) throws URISyntaxException {
        boolean isconverted = MediaController.getInstance().convertVideo(videoFileUri.getPath(), new File(destinationDir), startTime, endTime, outWidth, outHeight, bitrate);
        if (isconverted) {
            Log.v(LOG_TAG, "Video Conversion Complete");
        } else {
            Log.v(LOG_TAG, "Video conversion in progress");
        }

        return MediaController.cachedFile.getPath();

    }


    /**
     * Perform background video compression. Make sure the videofileUri and destinationUri are valid
     * resources because this method does not account for missing directories hence your converted file
     * could be in an unknown location
     *
     * @param videoFilePath  source path for the video file
     * @param destinationDir destination directory where converted file should be saved
     * @param startTime      start compression from a start time video (in microseconds) - from trimming purpose only
     * @param endTime        end the compression from a end time video (in microseconds) - from trimming purpose only
     * @param outWidth       the target width of the compressed video or 0 to use default width
     * @param outHeight      the target height of the compressed video or 0 to use default height
     * @param bitrate        the target bitrate of the compressed video or 0 to user default bitrate
     * @return The Path of the compressed video file
     */
    public String compressVideo(String videoFilePath, String destinationDir, long startTime, long endTime, int outWidth, int outHeight, int bitrate) {
        boolean isconverted = MediaController.getInstance().convertVideo(videoFilePath, new File(destinationDir), startTime, endTime, outWidth, outHeight, bitrate);
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
