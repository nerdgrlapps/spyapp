package org.nerdgrl.spycamera;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private ListView lvPhotos;
    private File[] mPhotos;
    private LayoutInflater mInflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mInflater = LayoutInflater.from(this);

        setupAlarmManager();

        lvPhotos = (ListView) findViewById(R.id.list_photos);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_update) {
            updatePhotos();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupAlarmManager() {
        PendingIntent pi = PendingIntent.getService(
                this,
                101,
                new Intent(this, CameraService.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 30 * 1000, pi);
    }

    private void updatePhotos() {

        new AsyncTask<Void, Void, Void>() {

            private FilenameFilter mFilter = new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    return s.endsWith(".jpg");
                }
            };

            @Override
            protected Void doInBackground(Void... voids) {
                File photosDir = CameraManager.getDir();
                if(photosDir.exists() && photosDir.isDirectory())
                    mPhotos = photosDir.listFiles(mFilter);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                PhotoAdapter adapter = new PhotoAdapter();
                lvPhotos.setAdapter(adapter);
            }

        }.execute();

    }

    class PhotoAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mPhotos != null ? mPhotos.length : 0;
        }

        @Override
        public Object getItem(int i) {
            return mPhotos != null ? mPhotos[i] : mPhotos;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            View root = mInflater.inflate(R.layout.item_photo, viewGroup, false);
            ImageView photo = (ImageView) root.findViewById(R.id.photo_image);
            TextView text = (TextView) root.findViewById(R.id.photo_text);
            File f = (File) getItem(i);
            if(f != null) {
                photo.setImageURI(Uri.parse("file://" + f.getPath()));
                String filename = f.getName();
                String label = null;
                filename = filename.replace("spyapp_", "");
                filename = filename.replace(".jpg", "");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
                try {
                    Date photoDate = sdf.parse(filename);
                    sdf.applyPattern("hh:mm aa\nMMMM, dd, yyyy");
                    label = sdf.format(photoDate);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                text.setText(label != null ? label : filename);
            }
            return root;
        }
    }
}
