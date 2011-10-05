package idv.Zero.MetaLoc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Random;

public class MainActivity extends Activity
{
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private TextView txtLocation;
    private Button btnStart, btnStop;
    private boolean mUpdateEnabled = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        ((ImageView) findViewById(R.id.imgViewOrg)).setImageDrawable(Resources.getSystem().getDrawable(android.R.drawable.ic_menu_compass));
        ((ImageView) findViewById(R.id.imgViewBW)).setImageDrawable(Resources.getSystem().getDrawable(android.R.drawable.ic_menu_compass));

        btnStart = ((Button)findViewById(R.id.btnStart));
        btnStop = ((Button)findViewById(R.id.btnStop));
        btnStop.setEnabled(false);

        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mUpdateEnabled = true;
                Intent intent = new Intent("org.metawatch.manager.APPLICATION_START");
                sendBroadcast(intent);
                sendUpdateMapMessage(0);
                btnStart.setEnabled(false);
                btnStop.setEnabled(true);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mUpdateEnabled = false;
                mHandler.removeMessages(1);
                Intent intent = new Intent("org.metawatch.manager.APPLICATION_STOP");
                sendBroadcast(intent);
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            }
        });

        txtLocation = (TextView) findViewById(R.id.txtLocation);
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new MetaLocLocationListener();
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
    }

    public class MetaLocLocationListener implements LocationListener
    {
        public void onLocationChanged(Location location) {
            if (!isBetterLocation(location, lastKnownLocation))
                return;

            txtLocation.setText(String.format("%f, %f on %s", location.getLatitude(), location.getLongitude(), new Date().toLocaleString()));
            lastKnownLocation = location;

            if (mUpdateEnabled)
                sendUpdateMapMessage(0);
        }

        /* I don't care! */
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        public void onProviderEnabled(String s) {
        }

        public void onProviderDisabled(String s) {
        }


        /* Straight from Google' Document */

        private static final int TWO_MINUTES = 1000 * 60 * 2;

        /** Determines whether one Location reading is better than the current Location fix
          * @param location  The new Location that you want to evaluate
          * @param currentBestLocation  The current Location fix, to which you want to compare the new one
          */
        protected boolean isBetterLocation(Location location, Location currentBestLocation) {
            if (currentBestLocation == null) {
                // A new location is always better than no location
                return true;
            }

            // Check whether the new location fix is newer or older
            long timeDelta = location.getTime() - currentBestLocation.getTime();
            boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
            boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
            boolean isNewer = timeDelta > 0;

            // If it's been more than two minutes since the current location, use the new location
            // because the user has likely moved
            if (isSignificantlyNewer) {
                return true;
            // If the new location is more than two minutes older, it must be worse
            } else if (isSignificantlyOlder) {
                return false;
            }

            // Check whether the new location fix is more or less accurate
            int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
            boolean isLessAccurate = accuracyDelta > 0;
            boolean isMoreAccurate = accuracyDelta < 0;
            boolean isSignificantlyLessAccurate = accuracyDelta > 200;

            // Check if the old and new location are from the same provider
            boolean isFromSameProvider = isSameProvider(location.getProvider(),
                    currentBestLocation.getProvider());

            // Determine location quality using a combination of timeliness and accuracy
            if (isMoreAccurate) {
                return true;
            } else if (isNewer && !isLessAccurate) {
                return true;
            } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
                return true;
            }
            return false;
        }

        /** Checks whether two providers are the same */
        private boolean isSameProvider(String provider1, String provider2) {
            if (provider1 == null) {
              return provider2 == null;
            }
            return provider1.equals(provider2);
        }
    }

    private void sendUpdateMapMessage(int millis)
    {
        mHandler.removeMessages(1);
        Message msg = new Message();
        msg.what = 1;
        if (millis > 0)
            mHandler.sendMessageDelayed(msg, millis);
        else
            mHandler.sendMessage(msg);
    }

    private Location lastKnownLocation;
    private void updateMap()
    {
        if (lastKnownLocation == null || !mUpdateEnabled)
            return;
        Bitmap bmp = null;

        Log.i("MetaLoc", "Fetching image...");
        try {
            String strURL = String.format("http://maps.google.com/maps/api/staticmap?center=%f,%f&zoom=17&size=96x96&maptype=roadmap&sensor=true&language=zh-TW", lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            URL remoteURL = new URL(strURL);
            InputStream is = (InputStream) remoteURL.getContent();
            bmp = BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (bmp != null)
        {
            Log.i("MetaLoc", "Converting to B&W...");
            int[] watchFB = new int[96 * 96];
            Bitmap bmpBW = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
            for (int i = 0; i < bmp.getWidth(); i++) {
                for (int j = 0; j < bmp.getHeight(); j++) {
                    int clrNum = bmp.getPixel(i, j);
                    float r = Color.red(clrNum), g = Color.green(clrNum), b = Color.blue(clrNum);
                    int luma = (int) (r * 0.3 + g * 0.59 + b * 0.11);
                    if (luma < 230) {
                        watchFB[j * bmp.getHeight() + i] = Color.BLACK;
                        bmpBW.setPixel(i, j, Color.BLACK);
                    } else {
                        watchFB[j * bmp.getHeight() + i] = Color.WHITE;
                        bmpBW.setPixel(i, j, Color.WHITE);
                    }
                }
            }

            Log.i("MetaLoc", "Sending framebuffer...");
            Intent intent = new Intent("org.metawatch.manager.APPLICATION_UPDATE");
            Bundle b = new Bundle();
            b.putIntArray("array", watchFB);
            intent.putExtras(b);
            sendBroadcast(intent);

            Log.i("MetaLoc", "FB away!");
            ((ImageView) findViewById(R.id.imgViewOrg)).setImageBitmap(bmp);
            ((ImageView) findViewById(R.id.imgViewBW)).setImageBitmap(bmpBW);
            sendUpdateMapMessage(5000);
        }
        else
        {
            Log.i("MetaLoc", "Failed to get map, random wait.");
            sendUpdateMapMessage(new Random().nextInt(10) * 1000);
        }
    }

    private static int MSG_UPDATE_MAP = 1;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg)
        {
            switch (msg.what) {
                case 1:
                    updateMap();
                    break;
            }
        }
    };

}
