package videocompression;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @Author By Jorge E. Hernandez (@lalongooo) 2015
 * @Co-Author Akah Larry (@larrytech7) 2017
 */

public class MediaController {

    public static final String TAG = MediaController.class.getSimpleName();
    public final static String MIME_TYPE = "video/avc";
    /**
     * volatile: Essentially, volatile is used to indicate that a variable's value will be modified by different threads.
     **/
    private static volatile MediaController Instance = null;

    /**
     * Default video parameters
     **/
    private final static int DEFAULT_VIDEO_WIDTH = 1280;
    private final static int DEFAULT_VIDEO_HEIGHT = 720;
    private final static int DEFAULT_VIDEO_BITRATE = 3072 * 1024;
    private final static int DEFAULT_FRAME_RATE = 30;
    private final static int DEFAULT_KEY_FRAME_INTERVAL = 2; //each "2" seconds (time interval) create a new Key frame

    /**
     * TODO understand this boolean value
     **/
    private boolean videoConvertFirstWrite = true;

    private String path;
    public static File cachedFile;

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
     * TODO not being used - currently we are using an AsyncTask( on MainActivity), syncronize object (on SiliCompressorl)
     * check what approach is the best for this case scenario (VideoConvertRunnable or AsyncTask)
     **/
    public static class VideoConvertRunnable implements Runnable {

        private String videoPath;
        private File destDirectory;

        private VideoConvertRunnable(String videoPath, File dest) {
            this.videoPath = videoPath;
            this.destDirectory = dest;
        }

        public static void runConversion(final String videoPath, final File dest) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        VideoConvertRunnable wrapper = new VideoConvertRunnable(videoPath, dest);
                        Thread th = new Thread(wrapper, "VideoConvertRunnable");
                        th.start();
                        th.join();
                    } catch (Exception e) {
                        Log.e("tmessages", e.getMessage());
                    }
                }
            }).start();
            Log.i(TAG, "VideoConvertRunnable wrapper has on start mode");
        }

        @Override
        public void run() {
            Log.i(TAG, "Thread is on run method");
            MediaController.getInstance().convertVideo(videoPath, destDirectory);
        }
    }

    /**
     * Boolean method flag
     **/
    private void didWriteData(final boolean last, final boolean error) {
        final boolean firstWrite = videoConvertFirstWrite;
        if (firstWrite) {
            videoConvertFirstWrite = false;
        }
    }

    private long readAndWriteTrack(MediaExtractor extractor, MP4Builder mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean isAudio) throws Exception {
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
     * Perform the actual video compression. Processes the frames and does the magic
     * Width, height and bitrate are now default
     *
     * @param sourcePath the source uri for the file as per
     * @param destDir    the destination directory where compressed video is eventually saved
     * @return
     */
    public boolean convertVideo(final String sourcePath, File destDir) {
        return convertVideo(sourcePath, destDir, 0, 0, 0);
    }

    /**
     * Perform the actual video compression. Processes the frames and does the magic
     *
     * @param sourcePath the source uri for the file as per
     * @param destDir    the destination directory where compressed video is eventually saved
     * @param outWidth   the target width of the converted video, 0 is default
     * @param outHeight  the target height of the converted video, 0 is default
     * @param outBitrate the target bitrate of the converted video, 0 is default
     * @return
     */

    public boolean convertVideo(final String sourcePath, File destDir, int outWidth, int outHeight, int outBitrate) {
        Log.i(TAG, "source path: " + sourcePath + ", destDir: " + destDir.getAbsolutePath());
        path = sourcePath;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

        long startTime = -1;
        long endTime = -1;

        int resultWidth = outWidth > 0 ? outWidth : DEFAULT_VIDEO_WIDTH;
        int resultHeight = outHeight > 0 ? outHeight : DEFAULT_VIDEO_HEIGHT;

        int rotationValue = Integer.valueOf(rotation);
        int originalWidth = Integer.valueOf(width);
        int originalHeight = Integer.valueOf(height);

        Log.i(TAG, "Metadata retriever ORIGINAL values, width: " + originalWidth + ", height : " + originalHeight + ", rotation: " + rotationValue);

        int bitrate = outBitrate > 0 ? outBitrate : DEFAULT_VIDEO_BITRATE;
        int rotateRender = 0;
        Log.i(TAG, "Convert Video with RESULT width: " + resultWidth + ", height: " + resultHeight + ", and a bitrate: " + bitrate);

        File cacheFile = new File(destDir, "COMP_VIDEO_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4");

        /**TODO why the API constraint? - don't understand this values**/
        if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 270;
            } else if (rotationValue == 180) {
                rotateRender = 180;
                rotationValue = 0;
            } else if (rotationValue == 270) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 90;
            }
        }

        File inputFile = new File(path);
        if (!inputFile.canRead()) {
            didWriteData(true, true);
            return false;
        }

        videoConvertFirstWrite = true;
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

                if (resultWidth != originalWidth || resultHeight != originalHeight) {
                    int videoIndex;
                    videoIndex = selectTrack(extractor, false);

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
                            int swapUV = 0;
                            int videoTrackIndex = -5;

                            int colorFormat;
                            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

                            Log.e("tmessages", "colorFormat = " + colorFormat);

                            int resultHeightAligned = resultHeight;
                            int padding = 0;
                            int bufferSize = resultWidth * resultHeight * 3 / 2;
                            if (resultHeight % 16 != 0) {
                                resultHeightAligned += (16 - (resultHeight % 16));
                                padding = resultWidth * (resultHeightAligned - resultHeight);
                                bufferSize += padding * 5 / 4;
                            }
                            Log.i(TAG, "converting video values resultHeightAligned: " + resultHeightAligned + ", resultHeight: " + resultHeight + ", padding: " + padding + ", bufferSize: " + bufferSize);

                            extractor.selectTrack(videoIndex);
                            if (startTime > 0) {
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else {
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }
                            MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);

                            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, DEFAULT_FRAME_RATE);
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, DEFAULT_KEY_FRAME_INTERVAL);

                            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                            /**Why configure method has Surface argument set to null?**/
                            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                            if (Build.VERSION.SDK_INT >= 18) {
                                inputSurface = new InputSurface(encoder.createInputSurface());
                                inputSurface.makeCurrent();
                            }
                            encoder.start();
                            Log.i(TAG, "encoder has started");

                            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
                            /**Check this if else condition**/
                            if (Build.VERSION.SDK_INT >= 18) {
                                outputSurface = new OutputSurface();
                            } else {
                                outputSurface = new OutputSurface(resultWidth, resultHeight, rotateRender);
                            }
                            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
                            decoder.start();
                            Log.i(TAG, "decoder has started");

                            final int TIMEOUT_USEC = 2500;
                            ByteBuffer[] decoderInputBuffers = null;
                            ByteBuffer[] encoderOutputBuffers = null;
                            ByteBuffer[] encoderInputBuffers = null;
                            if (Build.VERSION.SDK_INT < 21) {
                                decoderInputBuffers = decoder.getInputBuffers();
                                encoderOutputBuffers = encoder.getOutputBuffers();
                            }

                            int outputDoneCycles = 0;
                            while (!outputDone) {
                                outputDoneCycles++;
                                Log.i("CYCLE", "outputDone = false cycle number: " + outputDoneCycles);
                                if (!inputDone) {
                                    Log.i("TAG", "relax, inputDone is still false, we are decoding the original file");
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
                                            Log.i(TAG, "inputBufferId: " + inputBufIndex + ", chunkSize: " + chunkSize);
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
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                            inputDone = true;
                                        }
                                    }
                                }

                                boolean decoderOutputAvailable = !decoderDone;
                                boolean encoderOutputAvailable = true;
                                /**This while cycle makes no sense, it will be executed one time only because encoderOutputAvailable is already set to true, unless encoderStatus = INFO_TRY_AGAIN_LATER**/
                                while (decoderOutputAvailable || encoderOutputAvailable) {
                                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                    Log.i(TAG, "encoder status number: " + encoderStatus);
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
                                        Log.i(TAG, "BufferInfo object: " + info);
                                        Log.i(TAG, "BufferInfo flags integer value: " + info.flags);
                                        Log.i(TAG, "Video Track index: " + videoTrackIndex);
                                        if (info.size > 1) {
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                                if (mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, false)) {
                                                    didWriteData(false, false);
                                                }
                                            } else if (videoTrackIndex == -5) {
                                                /**csd: codec specific data
                                                 * sps: sequence parameter set
                                                 * pps: picture parameter set
                                                 * **/
                                                byte[] csd = new byte[info.size];
                                                encodedData.limit(info.offset + info.size);
                                                encodedData.position(info.offset);
                                                encodedData.get(csd);

                                                Log.i(TAG, "csd byte array: " + csd);
                                                for (int i = 0; i < csd.length; i++) {
                                                    Log.i(TAG, "csd[" + i + "] = " + csd[i]);
                                                }
                                                Log.i(TAG, "csd byte array size: " + csd.length);
                                                ByteBuffer sps = null;
                                                ByteBuffer pps = null;
                                                for (int a = info.size - 1; a >= 0; a--) {
                                                    Log.i(TAG, "for cycle a: " + a);
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
                                                Log.i(TAG, "media format created with csd-0 and csd-1 values... video track index: " + videoTrackIndex);
                                            }
                                        }
                                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                        Log.i(TAG, "is output done?: " + outputDone);
                                        encoder.releaseOutputBuffer(encoderStatus, false);
                                    }
                                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        continue;
                                    }

                                    if (!decoderDone) {
                                        Log.i(TAG, "Decoder not done");
                                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                        Log.i(TAG, "Decoder Status value integer: " + decoderStatus);
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
                                            boolean doRender;
                                            /**Don't understand this build version condition constraint**/
                                            if (Build.VERSION.SDK_INT >= 18) {
                                                doRender = info.size != 0;
                                            } else {
                                                doRender = info.size != 0 || info.presentationTimeUs != 0;
                                            }
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
                                                    Log.i(TAG, "OUTPUT surface is wating for new image");
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
                    long videoTime = readAndWriteTrack(extractor, mediaMuxer, info, startTime, endTime, cacheFile, false);
                    Log.i(TAG, "original resolutions == result resolutions... videoTime value: " + videoTime);
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }
                }
                if (!error) {
                    readAndWriteTrack(extractor, mediaMuxer, info, videoStartTime, endTime, cacheFile, true);
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
            didWriteData(true, true);
            return false;
        }
        didWriteData(true, error);

        cachedFile = cacheFile;

       /* File fdelete = inputFile;
        if (fdelete.exists()) {
            if (fdelete.delete()) {
               Log.e("file Deleted :" ,inputFile.getPath());
            } else {
                Log.e("file not Deleted :" , inputFile.getPath());
            }
        }*/

        //inputFile.delete();
        Log.e("ViratPath", path + "");
        Log.e("ViratPath", cacheFile.getPath() + "");
        Log.e("ViratPath", inputFile.getPath() + "");


       /* Log.e("ViratPath",path+"");
        File replacedFile = new File(path);

        FileOutputStream fos = null;
        InputStream inputStream = null;
        try {
            fos = new FileOutputStream(replacedFile);
             inputStream = new FileInputStream(cacheFile);
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            inputStream.close();
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
*/

        //    cacheFile.delete();

       /* try {
           // copyFile(cacheFile,inputFile);
            //inputFile.delete();
            FileUtils.copyFile(cacheFile,inputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        // cacheFile.delete();
        // inputFile.delete();
        return true;
    }

}
