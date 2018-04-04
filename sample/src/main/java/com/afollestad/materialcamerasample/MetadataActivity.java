package com.afollestad.materialcamerasample;

import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;

public class MetadataActivity extends AppCompatActivity {

    static final String EXTRA_VIDEO_PATH = "EXTRA_VIDEO_PATH";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metadata);
        TextView textView = findViewById(R.id.metadataTextView);

        Intent extraIntent = getIntent();
        String path = "";
        if (extraIntent != null) {
            path = extraIntent.getStringExtra(EXTRA_VIDEO_PATH);
            Log.i("TRIMMER", path);
        }

        try {
            String text = getMetadata(path);
            textView.setText(text);

        } catch (IOException e) {
            e.printStackTrace();
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    private String getMetadata(String path) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(path);

        int audioTrack = selectTrack(extractor, true);
        long audioSize = getTrackSize(extractor, audioTrack);
        int videoTrack = selectTrack(extractor, false);
        long videoSize = getTrackSize(extractor, videoTrack);

        String audioSizeStr = "Audio Track Size: " + readableFileSize(audioSize);
        String videoSizeStr = "\nVideo Track Size: " + readableFileSize(videoSize);

        return audioSizeStr + videoSizeStr;
    }

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

    private long getTrackSize(MediaExtractor extractor, int track) {
        long trackSize = 0;
        extractor.selectTrack(track);
        MediaFormat trackFormat = extractor.getTrackFormat(track);
        int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(maxBufferSize);
        while (extractor.readSampleData(byteBuffer, 0) > 0) {
            trackSize += extractor.readSampleData(byteBuffer, 0);
            extractor.advance();
        }
        Log.i("metadata ", "" + trackSize);
        extractor.unselectTrack(track);
        return trackSize;
    }

    private String readableFileSize(long size) {
        if (size <= 0) return size + " B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.##").format(size / Math.pow(1024, digitGroups))
                + " "
                + units[digitGroups];
    }

}
