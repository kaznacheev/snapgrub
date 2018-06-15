package org.snapgrub.android;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static android.support.v4.content.FileProvider.getUriForFile;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SnapGrub";

    private static final String EXPORT_DIRECTORY = "SnapGrub";
    private static final String EXPORT_PREFIX = "collage";

    private static final String CAPTURE_DIRECTORY = "capture";
    private static final String CAPTURE_PREFIX = "capture";

    private static final String SHARE_DIRECTORY = "share";
    private static final String SHARE_PREFIX = "share";

    public static final String IMPORT_DIRECTORY = "import";
    public static final String IMPORT_PREFIX = "import";

    public static final String AUTHORITY = "org.snapgrub.android.fileprovider";

    private static final int CAPTURE_REQUEST_CODE = 1;
    private static final int PICK_REQUEST_CODE = 2;
    private static final int SAVE_REQUEST_CODE = 3;
    private static final int SHARE_REQUEST_CODE = 4;

    public static final int MAX_ROWS = 3;
    public static final int MAX_COLUMNS = 3;
    public static final int MAX_CELLS = MAX_ROWS * MAX_COLUMNS;

    public static final int JPEG_QUALITY = 85;

    private ViewGroup mGridView;
    private TextView mDateView;

    private CellData[] mCellData;
    private CellView[] mCellView;

    private int mActiveRows;
    private int mActiveColumns;
    private int mActiveCellIndex;

    private Uri mCaptureUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_clear).setOnClickListener(v -> clear());
        findViewById(R.id.button_flip).setOnClickListener(v -> rotate());
        findViewById(R.id.button_snap).setOnClickListener(v -> snap());
        findViewById(R.id.button_pick).setOnClickListener(v -> pick());
        findViewById(R.id.button_text).setOnClickListener(v -> text());
        findViewById(R.id.button_save).setOnClickListener(v -> save());
        findViewById(R.id.button_share).setOnClickListener(v -> share());

        findViewById(R.id.layout3x3).setOnClickListener(v -> changeCellLayout(3, 3));
        findViewById(R.id.layout3x2).setOnClickListener(v -> changeCellLayout(3, 2));
        findViewById(R.id.layout2x3).setOnClickListener(v -> changeCellLayout(2, 3));

        mGridView = findViewById(R.id.grid);
        mDateView = findViewById(R.id.date);

        mCellData = new CellData[MAX_CELLS];
        for (int c = 0; c != MAX_CELLS; c++) {
            mCellData[c] = new CellData();
        }

        mActiveRows = MAX_ROWS;
        mActiveColumns = MAX_COLUMNS;
        mActiveCellIndex = 0;

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }

        setupCellLayout();
    }

    private void changeCellLayout(int rows, int columns) {
        mActiveRows = rows;
        mActiveColumns = columns;
        setupCellLayout();
    }

    private void setupCellLayout() {
        if (mCellView != null) {
            mCellView[mActiveCellIndex].highlight(false);
        }

        mCellView = new CellView[mActiveRows * mActiveColumns];

        int nextCellIndex = 0;

        for (int r = 0; r != MAX_ROWS; r++) {
            final boolean activeRow = r < mActiveRows;
            final View row = mGridView.getChildAt(r);
            if (activeRow) {
                row.setVisibility(View.VISIBLE);
            } else {
                row.setVisibility(View.GONE);
            }

            for (int c = 0; c != MAX_COLUMNS; c++) {
                final boolean activeColumn = c < mActiveColumns;
                View cellWrapper = ((ViewGroup) row).getChildAt(c);

                final CellView cellView = (CellView)((ViewGroup) cellWrapper).getChildAt(0);
                if (activeRow && activeColumn) {
                    cellView.bind(mCellData[nextCellIndex]);
                    mCellView[nextCellIndex++] = cellView;
                    cellWrapper.setVisibility(View.VISIBLE);
                    cellView.setClickable(true);
                } else {
                    cellView.bind(null);
                    cellWrapper.setVisibility(View.GONE);
                }
            }
            mGridView.requestLayout();
        }

        if (mActiveCellIndex >= mCellView.length) {
            mActiveCellIndex = mCellView.length - 1;
        }

        mCellView[mActiveCellIndex].highlight(true);
        updateDate();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("rows", mActiveRows);
        outState.putInt("columns", mActiveColumns);
        outState.putInt("active", mActiveCellIndex);

        for (int c = 0; c != MAX_CELLS; c++) {
            outState.putBundle("cell" + c, mCellData[c].toBundle());
        }
    }

    private void restoreInstanceState(Bundle inState) {
        mActiveRows = inState.getInt("rows", mActiveRows);
        mActiveColumns = inState.getInt("columns", mActiveColumns);
        mActiveCellIndex = inState.getInt("active", mActiveCellIndex);

        for (int c = 0; c != MAX_CELLS; c++) {
            Bundle b = inState.getBundle("cell" + c);
            if (b != null) {
                mCellData[c].restoreState(b, getContentResolver());
            }
        }
    }

    private void setActiveCellData(CellData cellData) {
        mCellData[mActiveCellIndex] = cellData;
    }

    private CellData getActiveCellData() {
        return mCellData[mActiveCellIndex];
    }

    private CellView getActiveCellView() {
        return mCellView[mActiveCellIndex];
    }

    public void activateCell(int index) {
        if (mActiveCellIndex == index) {
            return;
        }
        getActiveCellView().highlight(false);
        mActiveCellIndex = index;
        getActiveCellView().highlight(true);
    }

    public void activateCell(CellView view) {
        for (int c = 0; c != mCellView.length; c++) {
            if (mCellView[c] == view) {
                activateCell(c);
                return;
            }
        }
        reportError("Cannot activate cell");
    }

    private void clear() {
        for (int d = 0; d != mCellData.length; d++) {
            mCellData[d] = new CellData();
        }
        for (int c = 0; c != mCellView.length; c++) {
            mCellView[c].bind(mCellData[c]);
        }
        activateCell(mCellView[0]);
        updateDate();
    }

    private void rotate() {
        getActiveCellData().rotate();
        getActiveCellView().invalidate();
    }

    public void snap() {
        File file = getNewFile(getFilesDir(), CAPTURE_DIRECTORY, CAPTURE_PREFIX);
        if (file == null) {
            return;
        }

        mCaptureUri = getUriForFile(this, AUTHORITY, file);

        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCaptureUri);
        captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(captureIntent, CAPTURE_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            reportError("Failed to resolve intent");
        }
    }

    private void pick() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(pickIntent, PICK_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            reportError("Failed to resolve intent");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            reportError("Intent failed: " + resultCode);
            return;
        }

        switch (requestCode) {
            case CAPTURE_REQUEST_CODE: {
                importImages(Collections.singletonList(mCaptureUri));
                break;
            }

            case PICK_REQUEST_CODE: {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    List<Uri> uris = new ArrayList<>();
                    for (int i = 0; i != clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null) {
                            uris.add(uri);
                        }
                    }
                    importImages(uris);
                } else if (data.getData() != null) {
                    importImages(Collections.singletonList(data.getData()));
                }
                break;
            }
        }
    }

    @Nullable
    private CellData loadCell(Uri source) {
        try {
            final ContentResolver resolver = getContentResolver();

            final ExifInterface exif = new ExifInterface(resolver.openInputStream(source));

            String timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (timestamp == null) {
                Log.e("VLAD", "No timestamp found in the photo");
                timestamp = new SimpleDateFormat("YYYY:MM:dd HH:mm:ss").format(new Date());
            }

            int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1);
            int height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1);

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            if (width <= 0 || height <= 0) {
                bmOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(resolver.openInputStream(source), null, bmOptions);
                width = bmOptions.outWidth;
                height = bmOptions.outHeight;
            }

            int SIZE_LIMIT = 1024;
            bmOptions.inSampleSize = Math.min(width, height) / SIZE_LIMIT;
            bmOptions.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeStream(resolver.openInputStream(source), null, bmOptions);
            if (bitmap == null) {
                return null;
            }

            File file = getNewFile(getCacheDir(), IMPORT_DIRECTORY, IMPORT_PREFIX);
            if (file == null) {
                return null;
            }

            saveBitmap(bitmap, file);

            CellData cellData = new CellData();
            cellData.load(getUriForFile(this, AUTHORITY, file), getContentResolver());
            if (!cellData.hasImage()) {
                return null;
            }
            cellData.setTimestamp(timestamp);
            return cellData;
        } catch (Exception e) {
            reportError(e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void importImages(List<Uri> uris) {
        List<CellData> cells = new ArrayList<>();
        for (Uri uri : uris) {
            CellData cell = loadCell(uri);
            if (cell != null) {
                cells.add(cell);
            }
        }

        cells.sort(Comparator.comparing(CellData::getTimestamp));

        for (CellData cellData : cells) {
            setActiveCellData(cellData);
            CellView cellView = getActiveCellView();
            cellData.scaleToFit(cellView.getWidth(), cellView.getHeight());
            cellView.bind(cellData);
            if (mActiveCellIndex == mCellView.length - 1) {
                break;
            }
            nextCell();
        }
        updateDate();
    }

    private void updateDate() {
        for (CellData cellData : mCellData) {
            final String timestamp = cellData.getTimestamp();
            if (timestamp != null) {
                mDateView.setText(timestamp.split(" ")[0].replace(':', '/'));
                return;
            }
        }
        mDateView.setText("");
    }

    private void nextCell() {
        if (mActiveCellIndex < mCellView.length - 1) {
            activateCell(mActiveCellIndex + 1);
        }
    }

    public void text() {

    }

    private void save() {
        if (mustRequestStorageAccess(SAVE_REQUEST_CODE)) {
            return;
        }
        File file = getNewFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), EXPORT_DIRECTORY, EXPORT_PREFIX);
        if (file == null) {
            return;
        }
        saveBitmap(createSnapshot(), file);
    }

    private void share() {
        if (mustRequestStorageAccess(SHARE_REQUEST_CODE)) {
            return;
        }

        File file = getNewFile(getCacheDir(), SHARE_DIRECTORY, SHARE_PREFIX);
        if (file == null) {
            return;
        }

        saveBitmap(createSnapshot(), file);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, getUriForFile(this, AUTHORITY, file));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share to"));
    }

    public boolean mustRequestStorageAccess(int requestCode) {
        if (Build.VERSION.SDK_INT < 23) { //
            // permission is automatically granted on sdk<23 upon installation
            return false;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.v(LOG_TAG, "Permission: " + permissions[0] + " was " + grantResults[0]);
            switch (requestCode) {
                case SAVE_REQUEST_CODE:
                    save();
                    break;

                case SHARE_REQUEST_CODE:
                    share();
                    break;
            }
        }
    }

    @NonNull
    private Bitmap createSnapshot() {
        Bitmap bitmap = Bitmap.createBitmap(
                mGridView.getWidth(), mGridView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        getActiveCellView().highlight(false);
        mGridView.draw(canvas);
        getActiveCellView().highlight(true);
        return bitmap;
    }

    private void saveBitmap(Bitmap bitmap, File file) {
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            reportError(e.getMessage());
            e.printStackTrace();
        }
    }

    @Nullable
    private File getNewFile(File root, String dirName, String filePrefix) {
        File dir = new File(root, dirName);
        if (!dir.exists() && !dir.mkdirs()) {
            reportError("Failed to create " + dir);
            return null;
        }
        File file = new File(dir, getTimestampedFileName(filePrefix));
        if (file.exists() && !file.delete()) {
            reportError("Failed to delete " + file);
            return null;
        }
        return file;
    }

    @NonNull
    private String getTimestampedFileName(String prefix) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return prefix + "_" + timestamp + ".jpg";
    }

    private void reportError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.e(LOG_TAG, message);
    }
}
