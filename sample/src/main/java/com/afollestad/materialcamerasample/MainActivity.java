package com.afollestad.materialcamerasample;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialcamera.MaterialCamera;

import java.io.File;
import java.net.URISyntaxException;
import java.text.DecimalFormat;

import utils.FileUtils;
import videocompression.MediaController;
import videocompression.SiliCompressor;

/**
 * @author Aidan Follestad (afollestad)
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int CAMERA_RQ = 6969;
    private static final int PERMISSION_RQ = 84;
    private static final int REQUEST_VIDEO_GALLERY = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        findViewById(R.id.loadFromGallery).setOnClickListener(this);
        findViewById(R.id.launchCamera).setOnClickListener(this);
        findViewById(R.id.launchCameraStillshot).setOnClickListener(this);
        findViewById(R.id.launchFromFragment).setOnClickListener(this);
        findViewById(R.id.launchFromFragmentSupport).setOnClickListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission to save videos in external storage
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_RQ);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_RQ);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.loadFromGallery) {
            pickFromGallery();
            return;

        }
        if (view.getId() == R.id.launchFromFragment) {
            Intent intent = new Intent(this, FragmentActivity.class);
            startActivity(intent);
            return;
        }
        if (view.getId() == R.id.launchFromFragmentSupport) {
            Intent intent = new Intent(this, FragmentActivity.class);
            intent.putExtra("support", true);
            startActivity(intent);
            return;
        }

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
                        .showPortraitWarning(true)
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
     * TODO miss pick photo from Gallery
     * Select video file from Gallery
     **/
    private void pickFromGallery() {
        Intent intent = new Intent();
        intent.setTypeAndNormalize("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Gallery"), REQUEST_VIDEO_GALLERY);
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

        if (requestCode == REQUEST_VIDEO_GALLERY) {
            if (resultCode == RESULT_OK) {
                /*String videoPathString = FileUtils.getPath(this, data.getData());
                final File fileFromGallery = new File(videoPathString);
                Log.i("MAIN ACTIVITY", videoPathString);*/

                /**Do compression
                 * Compressed video is been saved in Material Camera directory**/
                Log.i("MAIN ACT", "before");
                new VideoCompressAsyncTask(this).execute(data.getData().toString(), new File(Environment.getExternalStorageDirectory(), "MaterialCamera").getAbsolutePath());

                Log.i("MAIN ACTIVITY", "after video compress async task");
            } else {
                Log.e("MAIN ACTIVITY", "Shit happens");
            }
        }

        // Received recording or error from MaterialCamera
        if (requestCode == CAMERA_RQ) {
            if (resultCode == RESULT_OK) {
                final File file = new File(data.getData().getPath());
                Toast.makeText(
                        this,
                        String.format("Saved to: %s, size: %s", file.getAbsolutePath(), fileSize(file)),
                        Toast.LENGTH_LONG)
                        .show();
            } else if (data != null) {
                Exception e = (Exception) data.getSerializableExtra(MaterialCamera.ERROR_EXTRA);
                if (e != null) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
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

    class VideoCompressAsyncTask extends AsyncTask<String, String, String> {

        Context mContext;
        long startTime;
        long endTime;

        public VideoCompressAsyncTask(Context context) {
            mContext = context;
            startTime = System.currentTimeMillis();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... paths) {
            String filePath = null;
            try {
                filePath = SiliCompressor.with(mContext).compressVideo(Uri.parse(paths[0]), paths[1]);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            return filePath;
        }

        @Override
        protected void onPostExecute(String compressedFilePath) {
            super.onPostExecute(compressedFilePath);
            endTime = System.currentTimeMillis();
            Toast.makeText(mContext, "Video compressed time: " + getTimeDuration() / 1000 + " seconds, path: " + compressedFilePath, Toast.LENGTH_LONG).show();
            Log.i("Main Activity", "onPostExecute time seconds: " + getTimeDuration() / 1000);
        }

        public long getTimeDuration() {
            return endTime - startTime;
        }
    }
}
