package life.knowledge4.videotrimmer;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialcamerasample.MainActivity;
import com.afollestad.materialcamerasample.R;

import java.io.File;

import life.knowledge4.videotrimmer.interfaces.OnK4LVideoListener;
import life.knowledge4.videotrimmer.interfaces.OnTrimVideoListener;

public class TrimmerActivity extends AppCompatActivity implements OnTrimVideoListener, OnK4LVideoListener {

    private K4LVideoTrimmer mVideoTrimmer;
    static final String EXTRA_VIDEO_PATH = "EXTRA_VIDEO_PATH";
    private ProgressDialog mProgressDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.afollestad.materialcamerasample.R.layout.activity_trimmer);

        Intent extraIntent = getIntent();
        String path = "";
        if (extraIntent != null) {
            path = extraIntent.getStringExtra(EXTRA_VIDEO_PATH);
            Log.i("TRIMMER", path);
        }
        //setting progressbar
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(getString(R.string.trimming_progress));

        mVideoTrimmer = ((K4LVideoTrimmer) findViewById(R.id.timeLine));
        if (mVideoTrimmer != null) {
            mVideoTrimmer.setMaxDuration(13);
            mVideoTrimmer.setOnTrimVideoListener(this);
            mVideoTrimmer.setOnK4LVideoListener(this);
            mVideoTrimmer.setVideoURI(Uri.parse(path));
            mVideoTrimmer.setVideoInformationVisibility(true);
        }
    }

    @Override
    public void getResult(final Uri uri) {
        Log.i("TRIMMER", "pressed save button");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TrimmerActivity.this, "Video saved at " + uri.getPath(), Toast.LENGTH_SHORT).show();
            }
        });
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setDataAndType(uri, "video/mp4");
        startActivity(intent);
        finish();
    }

    @Override
    public void getResult(final Uri uri, long startTime, long endTime) {
        Log.i("TRIMMER", "pressed save button");
        Log.i("TRIMMER", "st: " + startTime + ", et: " + endTime);
        Log.i("TRIMMER", "uri: " + uri);
        Log.i("TRIMMER", "uri: " + uri.getPath());

        new MainActivity.VideoCompressAsyncTask(this, startTime, endTime).execute(uri.getPath(), new File(Environment.getExternalStorageDirectory(), "MaterialCamera").getAbsolutePath());

        /*runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TrimmerActivity.this, "Video saved at " + uri.getPath(), Toast.LENGTH_SHORT).show();
            }
        });*/
        //Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        //intent.setDataAndType(uri, "video/mp4");
        //startActivity(intent);
        finish();
    }

    @Override
    public void cancelAction() {
        Log.i("TRIMMER", "pressed cancel button");
        mProgressDialog.cancel();
        mVideoTrimmer.destroy();
        finish();
    }

    @Override
    public void onVideoPrepared() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TrimmerActivity.this, "onVideoPrepared", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onError(final String message) {
        mProgressDialog.cancel();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TrimmerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onTrimStarted() {
        mProgressDialog.show();

    }
}
