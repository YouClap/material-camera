package videocompression;

/**
 * @Author By Jorge E. Hernandez (@lalongooo) 2015
 * @Co-Author Akah Larry (@larrytech7) 2017
 */

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MediaController {

    public static final String TAG = MediaController.class.getSimpleName();

    /**
     * Default video parameters
     **/
    private final static String MIME_TYPE = "video/avc";
    private final static int DEFAULT_VIDEO_WIDTH = 1280;
    private final static int DEFAULT_VIDEO_HEIGHT = 720;
    private final static int DEFAULT_VIDEO_BITRATE = 3072 * 1024;
    private final static int DEFAULT_FRAME_RATE = 30;
    private final static int DEFAULT_KEY_FRAME_INTERVAL = 2; //each "2" seconds (time interval) create a new Key frame

    /**
     * the new compressed file
     **/
    public static File cachedFile;

    /**
     * the original video string path
     **/
    public String path;

    /**
     * volatile: Essentially, volatile is used to indicate that a variable's value will be modified by different threads.
     **/
    private static volatile MediaController Instance = null;

    /**
     * Get a MediaController instance,
     * if there is no instance create it
     **/
    public static MediaController getInstance() {
        MediaController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MediaController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new MediaController();
                }
            }
        }
        return localInstance;
    }

    /**
     * Read data tracks (video track and audio track) from the @extractor object and write into a MP4Builder @mediaMuxer object
     *
     * @param extractor  MediaExtractor used to select the tracks to copy to the Mp4Buider object
     * @param mediaMuxer MP4Buider object used to store all the tracks readed from the extractor.
     * @param info       for each ByteBuffer created during the reading track period store all those ByteBuffers metadata into a BufferInfo object
     * @param start      start time (in microseconds). Start extracting track data from a startTime period
     * @param end        end time (in microseconds). End extracting track data from a endTime period
     * @param isAudio    boolean value - if it's a audio track then true, else false
     **/
    private long readAndWriteTrack(MediaExtractor extractor, MP4Builder mediaMuxer, MediaCodec.BufferInfo info, long start, long end, boolean isAudio) throws Exception {
        int trackIndex = selectTrack(extractor, isAudio);
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
            int muxerTrackIndex = mediaMuxer.addTrack(trackFormat, isAudio);
            int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            boolean inputDone = false;
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            long startTime = -1;

            while (!inputDone) {

                boolean eof = false;
                int index = extractor.getSampleTrackIndex();
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0);

                    if (info.size < 0) {
                        info.size = 0;
                        eof = true;
                    } else {
                        info.presentationTimeUs = extractor.getSampleTime();
                        if (start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, isAudio);
                            extractor.advance();
                        } else {
                            eof = true;
                        }
                    }
                } else if (index == -1) {
                    eof = true;
                }
                if (eof) {
                    inputDone = true;
                }
            }

            extractor.unselectTrack(trackIndex);
            return startTime;
        }
        return -1;
    }

    /**
     * Select track using MediaExtractor object to select audio tracks or video tracks
     *
     * @param extractor , MediaExtractor object
     * @param audio     , boolean value
     * @return Track number , integer
     * - if the extractor don't have an audio/video track returns -5 (error case)
     **/
    private int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    /**
     * Perform the actual video compression.
     * width, height and bitrate are now default
     * startTime and endTime are now default - start compressing the video from the beginning and end it till end of the file
     *
     * @param sourcePath the source uri for the file as per
     * @param destDir    the destination directory where compressed video is eventually saved
     * @return
     */
    public boolean convertVideo(final String sourcePath, File destDir) {
        return convertVideo(sourcePath, destDir, -1l, -1l, DEFAULT_VIDEO_WIDTH, DEFAULT_VIDEO_HEIGHT, DEFAULT_VIDEO_BITRATE);
    }

    /**
     * Perform the actual video compression.
     *
     * @param sourcePath the source uri for the file as per
     * @param destDir    the destination directory where compressed video is eventually saved
     * @param startTime  start compression from a start time video (in microseconds) - from trimming purpose only
     * @param endTime    end the compression from a end time video (in microseconds) - from trimming purpose only
     * @param outWidth   the target width of the converted video, 0 is default
     * @param outHeight  the target height of the converted video, 0 is default
     * @param outBitrate the target bitrate of the converted video, 0 is default
     * @return
     */
    public boolean convertVideo(final String sourcePath, File destDir, long startTime, long endTime, int outWidth, int outHeight, int outBitrate) {
        this.path = sourcePath;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
        Log.i(TAG, "original height: " + height + ", width: " + width + ", rotation: " + rotation + ", bitrate: " + bitrate);

        int resultWidth = outWidth > 0 ? outWidth : DEFAULT_VIDEO_WIDTH;
        int resultHeight = outHeight > 0 ? outHeight : DEFAULT_VIDEO_HEIGHT;

        int rotationValue = Integer.valueOf(rotation);
        int originalWidth = Integer.valueOf(width);
        int originalHeight = Integer.valueOf(height);

        int resultBitrate = outBitrate > 0 ? outBitrate : DEFAULT_VIDEO_BITRATE;

        File cacheFile = new File(destDir,
                "SILI_VIDEO_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4"
        );

        if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
            } else if (rotationValue == 180) {
                rotationValue = 0;
            } else if (rotationValue == 270) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
            }
        }

        File inputFile = new File(path);
        if (!inputFile.canRead()) {
            return false;
        }

        boolean error = false;
        long videoStartTime = startTime;

        long time = System.currentTimeMillis();

        if (resultWidth != 0 && resultHeight != 0) {
            MP4Builder mediaMuxer = null;
            MediaExtractor extractor = null;

            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                Mp4Movie movie = new Mp4Movie();
                movie.setCacheFile(cacheFile);
                movie.setRotation(rotationValue);
                movie.setSize(resultWidth, resultHeight);
                mediaMuxer = new MP4Builder().createMovie(movie);
                extractor = new MediaExtractor();
                extractor.setDataSource(inputFile.toString());
                Log.i(TAG, "convertVideo() - created mp4Movie object");

                if (resultWidth != originalWidth || resultHeight != originalHeight) {
                    int videoIndex;
                    videoIndex = selectTrack(extractor, false);
                    Log.i(TAG, "convertVideo() - select video track: " + videoIndex);

                    if (videoIndex >= 0) {
                        MediaCodec decoder = null;
                        MediaCodec encoder = null;
                        InputSurface inputSurface = null;
                        OutputSurface outputSurface = null;

                        try {
                            long videoTime = -1;
                            boolean outputDone = false;
                            boolean inputDone = false;
                            boolean decoderDone = false;
                            int videoTrackIndex = -5;
                            int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

                            extractor.selectTrack(videoIndex);
                            if (startTime > 0) {
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else {
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }
                            MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);
                            Log.i(TAG, "convertVideo() - media format INPUT: " + inputFormat.toString());

                            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, resultBitrate);
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, DEFAULT_FRAME_RATE);
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_KEY_FRAME_INTERVAL);
                            Log.i(TAG, "convertVideo() - media format INPUT: " + inputFormat.toString());

                            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                            /**Why configure method has Surface argument set to null?**/
                            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

                            inputSurface = new InputSurface(encoder.createInputSurface());
                            inputSurface.makeCurrent();

                            encoder.start();

                            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
                            outputSurface = new OutputSurface();

                            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
                            decoder.start();

                            final int TIMEOUT_USEC = 2500;
                            ByteBuffer[] decoderInputBuffers = null;
                            ByteBuffer[] encoderOutputBuffers = null;
                            if (Build.VERSION.SDK_INT < 21) {
                                decoderInputBuffers = decoder.getInputBuffers();
                                encoderOutputBuffers = encoder.getOutputBuffers();
                            }

                            while (!outputDone) {
                                if (!inputDone) {
                                    boolean eof = false;
                                    int index = extractor.getSampleTrackIndex();
                                    if (index == videoIndex) {
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            ByteBuffer inputBuf;
                                            if (Build.VERSION.SDK_INT < 21) {
                                                inputBuf = decoderInputBuffers[inputBufIndex];
                                            } else {
                                                inputBuf = decoder.getInputBuffer(inputBufIndex);
                                            }
                                            int chunkSize = extractor.readSampleData(inputBuf, 0);
                                            if (chunkSize < 0) {
                                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                inputDone = true;
                                            } else {
                                                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                                extractor.advance();
                                            }
                                        }
                                    } else if (index == -1) {
                                        eof = true;
                                    }
                                    /**Dont understand this condition, when will be runnable? eof is true just when videoTrack index is not found**/
                                    if (eof) {
                                        Log.e(TAG, "convertVideo EOF = TRUE");
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                            inputDone = true;
                                        }
                                    }
                                }
                                Log.i(TAG, "convertVideo() - !! INPUT IS DONE !!");

                                boolean decoderOutputAvailable = !decoderDone;
                                boolean encoderOutputAvailable = true;
                                /**This while cycle makes no sense, it will be executed one time only because encoderOutputAvailable is already set to true, unless encoderStatus = INFO_TRY_AGAIN_LATER**/
                                while (decoderOutputAvailable || encoderOutputAvailable) {
                                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        encoderOutputAvailable = false;
                                    }
                                    /**deprecated... INFO_OUTPUT_BUFFERS_CHANGED**/
                                    else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encoderOutputBuffers = encoder.getOutputBuffers();
                                        }
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                        MediaFormat newFormat = encoder.getOutputFormat();
                                        if (videoTrackIndex == -5) {
                                            videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                        }
                                    } else if (encoderStatus < 0) {
                                        throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                                    } else {
                                        ByteBuffer encodedData;
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encodedData = encoderOutputBuffers[encoderStatus];
                                        } else {
                                            encodedData = encoder.getOutputBuffer(encoderStatus);
                                        }
                                        if (encodedData == null) {
                                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                        }
                                        if (info.size > 1) {
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                                mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, false);

                                            } else if (videoTrackIndex == -5) {
                                                Log.i(TAG, "video track not created/found");
                                                /**csd: codec specific data
                                                 * sps: sequence parameter set
                                                 * pps: picture parameter set
                                                 * **/
                                                byte[] csd = new byte[info.size];
                                                encodedData.limit(info.offset + info.size);
                                                encodedData.position(info.offset);
                                                encodedData.get(csd);
                                                ByteBuffer sps = null;
                                                ByteBuffer pps = null;
                                                for (int a = info.size - 1; a >= 0; a--) {
                                                    if (a > 3) {
                                                        if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                            sps = ByteBuffer.allocate(a - 3);
                                                            pps = ByteBuffer.allocate(info.size - (a - 3));
                                                            sps.put(csd, 0, a - 3).position(0);
                                                            pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                                            break;
                                                        }
                                                    } else {
                                                        break;
                                                    }
                                                }

                                                MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                                                if (sps != null && pps != null) {
                                                    newFormat.setByteBuffer("csd-0", sps);
                                                    newFormat.setByteBuffer("csd-1", pps);
                                                }
                                                videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                            }
                                        }
                                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                        encoder.releaseOutputBuffer(encoderStatus, false);
                                    }
                                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        Log.i(TAG, "convertVideo() - try again later -> go to while loop again");
                                        continue;
                                    }

                                    if (!decoderDone) {
                                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            decoderOutputAvailable = false;
                                        }
                                        /**deprecated, nothing in here mate**/
                                        else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                            MediaFormat newFormat = decoder.getOutputFormat();
                                            Log.e("tmessages", "newFormat = " + newFormat);
                                        } else if (decoderStatus < 0) {
                                            throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                                        } else {
                                            boolean doRender = info.size != 0;
                                            Log.i(TAG, "convertVideo() - do render?: " + doRender);

                                            if (endTime > 0 && info.presentationTimeUs >= endTime) {
                                                inputDone = true;
                                                decoderDone = true;
                                                doRender = false;
                                                /**something like this: --> info.flags = info.flags | MediaCodec.Buffer_FLAG_END_OF_STREAM**/
                                                info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                            }
                                            if (startTime > 0 && videoTime == -1) {
                                                if (info.presentationTimeUs < startTime) {
                                                    doRender = false;
                                                    Log.e("tmessages", "drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs);
                                                } else {
                                                    videoTime = info.presentationTimeUs;
                                                }
                                            }
                                            decoder.releaseOutputBuffer(decoderStatus, doRender);
                                            if (doRender) {
                                                boolean errorWait = false;
                                                try {
                                                    outputSurface.awaitNewImage();
                                                } catch (Exception e) {
                                                    errorWait = true;
                                                    Log.e("tmessages", e.getMessage());
                                                }
                                                if (!errorWait) {
                                                    outputSurface.drawImage(false);
                                                    inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                                    inputSurface.swapBuffers();
                                                }
                                            }
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                                decoderOutputAvailable = false;
                                                Log.e("tmessages", "decoder stream end");
                                                encoder.signalEndOfInputStream();
                                            }
                                        }
                                    }
                                }
                            }
                            if (videoTime != -1) {
                                videoStartTime = videoTime;
                            }
                        } catch (Exception e) {
                            Log.e("tmessages", e.getMessage());
                            error = true;
                        }

                        extractor.unselectTrack(videoIndex);
                        Log.i(TAG, "unselect track : " + videoIndex + " - on extractor");

                        if (outputSurface != null) {
                            outputSurface.release();
                        }
                        if (inputSurface != null) {
                            inputSurface.release();
                        }
                        if (decoder != null) {
                            decoder.stop();
                            decoder.release();
                        }
                        if (encoder != null) {
                            encoder.stop();
                            encoder.release();
                        }
                    }
                } else {
                    Log.i(TAG, "we don't have to change to change resolutions");
                    long videoTime = readAndWriteTrack(extractor, mediaMuxer, info, startTime, endTime, false);
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }
                }
                if (!error) {
                    readAndWriteTrack(extractor, mediaMuxer, info, videoStartTime, endTime, true);
                    Log.i(TAG, "audio track here");
                }
            } catch (Exception e) {
                error = true;
                Log.e("tmessages", e.getMessage());
            } finally {
                if (extractor != null) {
                    extractor.release();
                }
                if (mediaMuxer != null) {
                    try {
                        mediaMuxer.finishMovie(false);
                    } catch (Exception e) {
                        Log.e("tmessages", e.getMessage());
                    }
                }
                Log.e("tmessages", "time = " + (System.currentTimeMillis() - time));
            }
        } else {
            return false;
        }
        cachedFile = cacheFile;
        return true;
    }

    public static void copyFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();
        try {
            inChannel.transferTo(1, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }
}