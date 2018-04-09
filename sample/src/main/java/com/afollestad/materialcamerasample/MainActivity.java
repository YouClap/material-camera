package com.afollestad.materialcamerasample;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialcamera.MaterialCamera;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import life.knowledge4.videotrimmer.TrimmerActivity;
import utils.FileUtils;
import videocompression.SiliCompressor;

/**
 * @author Aidan Follestad (afollestad)
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int CAMERA_RQ = 6969;
    private static final int PERMISSION_RQ = 84;
    private static final int REQUEST_VIDEO_METADATA = 1;
    private static final int PERMISSION_CAMERA = 69;
    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_VIDEO_CAPTURE = 200;
    private static final int REQUEST_IMAGE_GALLERY = 4;
    private static final int REQUEST_VIDEO_GALLERY = 2;
    private static final int REQUEST_VIDEO_TRIMMER = 3;

    private static final int TYPE_IMAGE = 10;
    private static final int TYPE_VIDEO = 20;
    private static final String EXTRA_VIDEO_URI = "EXTRA_VIDEO_URI";

    Uri capturedUri;

    private static final String IMAGE_SUFFIX = ".jpg";
    private static final String IMAGE_FORMAT_NAME = "JPEG_";
    private static final String VIDEO_SUFFIX = ".mp4";
    private static final String VIDEO_FORMAT_NAME = "VID_";

    private final String EXTRA_IMAGE_PATH = "EXTRA_IMAGE_PATH";
    static final String EXTRA_VIDEO_PATH = "EXTRA_VIDEO_PATH";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        findViewById(R.id.getVideoMetadata).setOnClickListener(this);
        findViewById(R.id.takePhoto).setOnClickListener(this);
        findViewById(R.id.takeVideo).setOnClickListener(this);
        findViewById(R.id.loadImageFromGallery).setOnClickListener(this);
        findViewById(R.id.loadFromGallery).setOnClickListener(this);
        findViewById(R.id.trimVideoFromGallery).setOnClickListener(this);
        findViewById(R.id.launchCamera).setOnClickListener(this);
        findViewById(R.id.launchCameraStillshot).setOnClickListener(this);
        //findViewById(R.id.launchFromFragment).setOnClickListener(this);
        //findViewById(R.id.launchFromFragmentSupport).setOnClickListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission to save videos in external storage
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_RQ);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_RQ);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.getVideoMetadata) {
            pickFromGallery(view, true, false);
            return;
        }

        if (view.getId() == R.id.takePhoto) {
            dispatchTakePictureIntent();
            return;
        }

        if (view.getId() == R.id.takeVideo) {
            dispatchTakeVideoIntent();
            return;
        }

        if (view.getId() == R.id.loadImageFromGallery) {
            pickFromGallery(view, false, true);
            return;
        }

        if (view.getId() == R.id.loadFromGallery) {
            pickFromGallery(view, true, false);
            return;
        }

        if (view.getId() == R.id.trimVideoFromGallery) {
            pickFromGallery(view, true, false);
            return;
        }

        /*if (view.getId() == R.id.launchFromFragment) {
            Intent intent = new Intent(this, FragmentActivity.class);
            startActivity(intent);
            return;
        }
        if (view.getId() == R.id.launchFromFragmentSupport) {
            Intent intent = new Intent(this, FragmentActivity.class);
            intent.putExtra("support", true);
            startActivity(intent);
            return;
        }*/

        File saveDir = null;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            // Only use external storage directory if permission is granted, otherwise cache directory is used by default
            saveDir = new File(Environment.getExternalStorageDirectory(), "MaterialCamera");
            saveDir.mkdirs();
        }

        MaterialCamera materialCamera =
                new MaterialCamera(this)
                        .saveDir(saveDir)
                        .showPortraitWarning(false)
                        .allowRetry(true)
                        .defaultToFrontFacing(true)
                        .allowRetry(true)
                        .autoSubmit(false)
                        .labelConfirm(R.string.mcam_use_video);

        if (view.getId() == R.id.launchCameraStillshot)
            materialCamera
                    .stillShot() // launches the Camera in stillshot mode
                    .labelConfirm(R.string.mcam_use_stillshot);
        materialCamera.start(CAMERA_RQ);
    }

    /**
     * Call Native Camera app to take a picture
     * create a image type file (.jpg) and add a content:// URI from the file created to the intent
     **/
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            //Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createMediaFile(TYPE_IMAGE);
            } catch (IOException ex) {
                // Error occurred while creating the FileF
                Log.e(TAG, "IOException: " + ex.getMessage());
            }

            //Continue only if photo file was sucessuflly created
            if (photoFile != null) {
                capturedUri = Uri.fromFile(photoFile);
                String authority = "com.afollestad.materialcamerasample.file_provider";
                Uri photoURI = FileProvider.getUriForFile(this, authority, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    /**
     * Call Native Camera app to record a video
     * create a video type file (.mp4) and add a content:// URI from the file created to the intent
     **/
    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            File videoFile = null;
            try {
                videoFile = createMediaFile(TYPE_VIDEO);
            } catch (IOException ex) {
                Log.e(TAG, "IOException " + ex.getMessage());
            }
            if (videoFile != null) {
                capturedUri = Uri.fromFile(videoFile);
                String authority = "com.afollestad.materialcamerasample.file_provider";
                Uri videoURI = FileProvider.getUriForFile(this, authority, videoFile);
                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoURI);
                takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30);
                startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
            }
        }
    }

    private File createMediaFile(int type) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = type == TYPE_IMAGE ? IMAGE_FORMAT_NAME + timeStamp + "_" : VIDEO_FORMAT_NAME + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(type == TYPE_IMAGE ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES);

        File file = File.createTempFile(fileName, type == TYPE_IMAGE ? IMAGE_SUFFIX : VIDEO_SUFFIX, storageDir);
        Log.d(TAG, "created media file without data for now: " + file.getAbsolutePath());
        return file;
    }

    /**
     * @param view view
     *             Select video/image file from Gallery
     **/
    private void pickFromGallery(View view, boolean isVideo, boolean isImage) {
        Intent intent = new Intent();
        if (isVideo)
            intent.setTypeAndNormalize("video/*");
        if (isImage)
            intent.setTypeAndNormalize("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (view.getId() == R.id.getVideoMetadata)
            startActivityForResult(Intent.createChooser(intent, "Gallery"), REQUEST_VIDEO_METADATA);
        else if (view.getId() == R.id.loadFromGallery)
            startActivityForResult(Intent.createChooser(intent, "Gallery"), REQUEST_VIDEO_GALLERY);
        else if (view.getId() == R.id.loadImageFromGallery)
            startActivityForResult(Intent.createChooser(intent, "Gallery"), REQUEST_IMAGE_GALLERY);
        else
            startActivityForResult(Intent.createChooser(intent, "Gallery"), REQUEST_VIDEO_TRIMMER);
    }

    private String readableFileSize(long size) {
        if (size <= 0) return size + " B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.##").format(size / Math.pow(1024, digitGroups))
                + " "
                + units[digitGroups];
    }

    private String fileSize(File file) {
        return readableFileSize(file.length());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "resultcode : " + resultCode);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_VIDEO_METADATA) {
                startMetadataActivity(FileUtils.getPath(this, data.getData()));
            } else if (requestCode == REQUEST_IMAGE_CAPTURE) {
                String photoFilePath = capturedUri.getPath();
                new ImageCompressAsyncTask(this).execute(photoFilePath, new File(Environment.getExternalStorageDirectory(), "MaterialCamera").getAbsolutePath());
            } else if (requestCode == REQUEST_VIDEO_CAPTURE) {
                String videoFilePath = capturedUri.getPath();
                Log.d(TAG, "activity result video capture uri: " + videoFilePath);
                startTrimActivity(videoFilePath);
            } else if (requestCode == REQUEST_IMAGE_GALLERY) {
                /**Do Image Compression**/
                new ImageCompressAsyncTask(this).execute(FileUtils.getPath(this, data.getData()), new File(Environment.getExternalStorageDirectory(), "MaterialCamera").getAbsolutePath());

            } else if (requestCode == REQUEST_VIDEO_GALLERY) {
                /**Do compression
                 * Compressed video is been saved in Material Camera directory**/
                Log.i("MAIN ACT", "before");
                new VideoCompressAsyncTask(this).execute(FileUtils.getPath(this, data.getData()), new File(Environment.getExternalStorageDirectory(), "MaterialCamera").getAbsolutePath());

                Log.i("MAIN ACTIVITY", "after video compress async task");
            } else if (requestCode == REQUEST_VIDEO_TRIMMER) {
                startTrimActivity(FileUtils.getPath(this, data.getData()));
            }
            // Received recording or error from MaterialCamera
            else if (requestCode == CAMERA_RQ) {
                final File file = new File(data.getData().getPath());
                Toast.makeText(
                        this,
                        String.format("Saved to: %s, size: %s", file.getAbsolutePath(), fileSize(file)),
                        Toast.LENGTH_LONG)
                        .show();
            }
        } else if (data != null)

        {
            Exception e = (Exception) data.getSerializableExtra(MaterialCamera.ERROR_EXTRA);
            if (e != null) {
                e.printStackTrace();
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else

        {
            Log.e(TAG, "FAILED ma friend");
        }
    }

    private void startMetadataActivity(String selectedFile) {
        Intent intent = new Intent(this, MetadataActivity.class);
        intent.putExtra(EXTRA_VIDEO_PATH, selectedFile);
        startActivity(intent);

    }

    private void startTrimActivity(String selectedFile) {
        Intent intent = new Intent(this, TrimmerActivity.class);
        intent.putExtra(EXTRA_VIDEO_PATH, selectedFile);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {

            // Sample was denied WRITE_EXTERNAL_STORAGE permission
            Toast.makeText(
                    this,
                    "Videos will be saved in a cache directory instead of an external storage directory since permission was denied.",
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static final class VideoCompressAsyncTask extends AsyncTask<String, String, String> {

        Context mContext;
        long compressionTime;
        long startTime;
        long endTime;
        int width;
        int height;
        int bitrate;

        public VideoCompressAsyncTask(Context context) {
            mContext = context;
            compressionTime = System.currentTimeMillis();
        }

        public VideoCompressAsyncTask(Context context, long startTime, long endTime) {
            mContext = context;
            this.startTime = startTime;
            this.endTime = endTime;
            this.width = 854;
            this.height = 480;
            this.bitrate = 1024 * 1024 * 2;
            compressionTime = System.currentTimeMillis();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... paths) {
            String filePath;
            if (startTime != 0 || endTime != 0) {
                filePath = SiliCompressor.with(mContext).compressVideo(paths[0], paths[1], startTime * 1000, endTime * 1000, width, height, bitrate);
            } else {
                filePath = SiliCompressor.with(mContext).compressVideo(paths[0], paths[1]);
            }
            return filePath;
        }

        @Override
        protected void onPostExecute(String compressedFilePath) {
            super.onPostExecute(compressedFilePath);
            compressionTime = System.currentTimeMillis() - compressionTime;
            Toast.makeText(mContext, "Video compressed time: " + getCompressionTimeDuration() / 1000 + " seconds, path: " + compressedFilePath, Toast.LENGTH_LONG).show();
            Log.i("Main Activity", "onPostExecute time seconds: " + compressionTime / 1000);
        }

        public long getCompressionTimeDuration() {
            return compressionTime;
        }
    }

    public static final class ImageCompressAsyncTask extends AsyncTask<String, String, String> {

        Context mContext;
        long compressionTime;
        int width;
        int height;
        int quality;

        public ImageCompressAsyncTask(Context context) {
            mContext = context;
            this.width = 640;
            this.height = 480;
            this.quality = 75;
            compressionTime = System.currentTimeMillis();
        }

        public ImageCompressAsyncTask(Context context, int width, int height, int quality) {
            mContext = context;
            this.width = width;
            this.height = height;
            this.quality = quality;
            compressionTime = System.currentTimeMillis();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... paths) {
            String filePath;
            filePath = SiliCompressor.with(mContext).compressImage(paths[0], paths[1], width, height, quality, false);
            return filePath;
        }

        @Override
        protected void onPostExecute(String compressedFilePath) {
            super.onPostExecute(compressedFilePath);
            compressionTime = System.currentTimeMillis() - compressionTime;
            Toast.makeText(mContext, "Video compressed time: " + getCompressionTimeDuration() / 1000 + " seconds, path: " + compressedFilePath, Toast.LENGTH_LONG).show();
            Log.i("Main Activity", "onPostExecute time seconds: " + compressionTime / 1000);
        }

        public long getCompressionTimeDuration() {
            return compressionTime;
        }
    }
}
