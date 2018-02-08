package com.example.borja.videorecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;
import java.net.URISyntaxException;

import static android.content.ContentValues.TAG;

public class other extends AppCompatActivity {
    private static final int VIDEO_CAPTURE = 101;
    // The TransferUtility is the primary class for managing transfer to S3
    private TransferUtility transferUtility;

    // Button for record and upload video
    Button btnGrabar, btnSubir;

    private Uri fileUri;

    AmazonS3Client s3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);

        s3 = Util.getS3Client(getApplicationContext());

        // Initializes TransferUtility, always do this before using it.
        transferUtility = Util.getTransferUtility(this);

        btnGrabar = (Button) findViewById(R.id.btnGrabar);
        btnSubir = (Button) findViewById(R.id.btnSubir);

        btnGrabar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ESTO NO MOLA....COMO PODEMOS MEJORARLO???
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Pedimos permiso para acceder a la camara por primera vez en caso de no tenerlo
                    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, VIDEO_CAPTURE);
                    } else
                        startRecording();
                }
            }
        });

        btnSubir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                if (Build.VERSION.SDK_INT >= 19) {
                    // For Android KitKat, we use a different intent to ensure
                    // we can
                    // get the file path from the returned intent URI
                    intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                    intent.setType("*/*");
                } else {
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    intent.setType("file/*");
                }

                startActivityForResult(intent, 0);
            }
        });


    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasCamera())
            btnGrabar.setEnabled(false);
    }

    private boolean hasCamera() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){
            return true;
        } else {
            return false;
        }
    }

    public void startRecording() {
        //File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/myvideo.mp4");

        //fileUri = FileProvider.getUriForFile(other.this, BuildConfig.APPLICATION_ID + ".provider", file);

        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        //intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, VIDEO_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();

            try {
                String path = getPath(uri);
                beginUpload(path);
            } catch (URISyntaxException e) {
                Toast.makeText(this,
                        "Unable to get the file from the given URI.  See error log for details", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Unable to upload file from the given uri", e);
            }
        }
    }

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = managedQuery(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    @SuppressLint("NewApi")
    private String getPath(Uri uri) throws URISyntaxException {
        final boolean needToCheckUri = Build.VERSION.SDK_INT >= 19;
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (needToCheckUri && DocumentsContract.isDocumentUri(getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                Log.e("OOOOOOOOOOOOOOO1", Environment.getExternalStorageDirectory() + "/" + split[1]);
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[] {
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    Log.e("OOOOOOOOOOOOOOO2", cursor.getString(column_index));
                    ///storage/emulated/0/DCIM/Camera/20180207_231835.mp4
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            Log.e("OOOOOOOOOOOOOOO3", uri.getPath());
            return uri.getPath();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /*
     * Begins to upload the file specified by the file path.
     */
    private void beginUpload(String filePath) {
        if (filePath == null) {
            Toast.makeText(this, "Could not find the filepath of the selected file", Toast.LENGTH_LONG).show();
            return;
        }
        Log.e("OOOOOOOOO: ", filePath);
        File file = new File(filePath);
        TransferObserver observer = transferUtility.upload(Constants.BUCKET_NAME, file.getName(), file);
        Toast.makeText(this, filePath, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == VIDEO_CAPTURE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Now user should be able to use camera
                startRecording();
            }
            else {
                // Your app will not have this permission. Turn off all functions
                // that require this permission or it will force close like your
                // original question
            }
        }
    }
}
