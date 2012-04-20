package idv.Zero.MetaLoc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.*;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Random;

public class MainActivity extends Activity {
  private LocationManager mLocationManager;
  private LocationListener mLocationListener;
  private EditText txtDestination;
  private TextView txtLocation;
  private Button btnStart, btnStop;
  private boolean mUpdateEnabled = false;

  /**
   * Called when the activity is first created.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    ((ImageView) findViewById(R.id.imgViewOrg)).setImageDrawable(Resources.getSystem().getDrawable(android.R.drawable.ic_menu_compass));
    ((ImageView) findViewById(R.id.imgViewBW)).setImageDrawable(Resources.getSystem().getDrawable(android.R.drawable.ic_menu_compass));

    txtDestination = (EditText) findViewById(R.id.txtDestination);
    btnStart = ((Button) findViewById(R.id.btnStart));
    btnStop = ((Button) findViewById(R.id.btnStop));
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
    mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    mLocationListener = new MetaLocLocationListener();
    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, mLocationListener);
    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, mLocationListener);
  }

  public class MetaLocLocationListener implements LocationListener {
    public void onLocationChanged(Location location) {
      if (!isBetterLocation(location, lastKnownLocation))
        return;

      if (lastKnownLocation != null) {
        location.setSpeed(location.distanceTo(lastKnownLocation) / (location.getTime() - lastKnownLocation.getTime()) * 3600 * 1000);
        location.setBearing(lastKnownLocation.bearingTo(location));
      }
      txtLocation.setText(String.format("%f, %f (spd: %f km/h, bearing: %f) on %s from %s",
              location.getLatitude(),
              location.getLongitude(),
              location.getSpeed() / 1000,
              location.getBearing(),
              new Date().toLocaleString(),
              location.getProvider())
      );
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

    private static final int HALF_MINUTES = 1000 * 30;

    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
      if (currentBestLocation == null) {
        // A new location is always better than no location
        return true;
      }

      // Check whether the new location fix is newer or older
      long timeDelta = location.getTime() - currentBestLocation.getTime();
      boolean isSignificantlyNewer = timeDelta > HALF_MINUTES;
      boolean isSignificantlyOlder = timeDelta < -HALF_MINUTES;
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

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
      if (provider1 == null) {
        return provider2 == null;
      }
      return provider1.equals(provider2);
    }
  }

  private void sendUpdateMapMessage(int millis) {
    mHandler.removeMessages(1);
    Message msg = new Message();
    msg.what = 1;
    if (millis > 0)
      mHandler.sendMessageDelayed(msg, millis);
    else
      mHandler.sendMessage(msg);
  }

  private Location lastKnownLocation;
  private Bitmap bmpOrg = null;
  private Boolean mNeedNewMap = true;

  private void updateMap() {
    if (lastKnownLocation == null || !mUpdateEnabled)
      return;

    if (bmpOrg == null || bmpOrg.isRecycled() || mNeedNewMap) {
      Log.i("MetaLoc", "Fetching image...");
      try {
        String strURL = String.format("http://maps.google.com/maps/api/staticmap?center=%f,%f&zoom=16&size=96x96&maptype=roadmap&sensor=true&language=zh-TW&&style=feature:road.highway|element:geometry|hue:0xffffff|saturation:-1000|lightness:100",
                lastKnownLocation.getLatitude(),
                lastKnownLocation.getLongitude()
        );
        Log.i("MetaLoc", "Map URL: " + strURL);
        URL remoteURL = new URL(strURL);
        InputStream is = (InputStream) remoteURL.getContent();
        bmpOrg = BitmapFactory.decodeStream(is);
      } catch (Exception e) {
        e.printStackTrace();
      }

      mNeedNewMap = false;
    }

    if (bmpOrg != null) {
      Bitmap bmp = Bitmap.createBitmap(bmpOrg.getWidth(), bmpOrg.getHeight(), bmpOrg.getConfig());
      int[] watchFB = new int[96 * 96];

      Log.i("MetaLoc", "Overlaying infobar...");
      Canvas c = new Canvas(bmp);
      c.drawBitmap(bmpOrg, 0, 0, getMapPaint());
      c.drawRect(new Rect(0, 0, bmp.getWidth(), 16), getBarPaint());
      int spd = (int) (lastKnownLocation.getSpeed() / 1000); /* use km/h */

      c.drawText(String.format("%d km/h", spd), bmp.getWidth() - 5, 12, getInfoTextPaint(Paint.Align.RIGHT));

      String[] txtDest = txtDestination.getText().toString().split(",");
      if (txtDest.length == 2) {
        Location locDest = new Location(LocationManager.GPS_PROVIDER);
        locDest.setLatitude(Double.parseDouble(txtDest[0]));
        locDest.setLongitude(Double.parseDouble(txtDest[1]));
        float distance = (int) lastKnownLocation.distanceTo(locDest);
        String du = "m";
        if (distance > 1000) {
          du = "km";
          distance /= 1000;
        }

        if (du.equalsIgnoreCase("km"))
          c.drawText(String.format("%.1f%s", distance, du), 16 + 5, 12, getInfoTextPaint(Paint.Align.LEFT));
        else if (du.equalsIgnoreCase("m"))
          c.drawText(String.format("%.0f%s", distance, du), 16 + 5, 12, getInfoTextPaint(Paint.Align.LEFT));

        c.save();
        int cx = 8, cy = 8;
        c.rotate(lastKnownLocation.bearingTo(locDest) - lastKnownLocation.getBearing(), cx, cy);
        Path pathArrow = new Path();
        pathArrow.moveTo(cx - 4, cy);
        pathArrow.lineTo(cx, cy - 5);
        pathArrow.lineTo(cx + 4, cy);
        c.drawPath(pathArrow, getInfoDirectionArrowPaint());
        c.restore();
      }

      /* drawing location */
      c.save();
      int cx = bmp.getWidth() / 2, cy = bmp.getHeight() / 2;
      c.rotate(lastKnownLocation.getBearing(), cx, cy);
      Paint pntLoc = new Paint();
      pntLoc.setAntiAlias(true);
      pntLoc.setStyle(Paint.Style.FILL);
      pntLoc.setColor(Color.WHITE);
      c.drawCircle(
        bmp.getWidth() / 2,
        bmp.getHeight() / 2,
        7,
        pntLoc
      );
      pntLoc.setColor(Color.BLACK);
      c.drawCircle(
        bmp.getWidth() / 2,
        bmp.getHeight() / 2,
        5,
        pntLoc
      );
      Path pathArrow = new Path();
      pathArrow.moveTo(cx - 5, cy - 6);
      pathArrow.lineTo(cx, cy - 11);
      pathArrow.lineTo(cx + 5, cy - 6);
      pntLoc.setStyle(Paint.Style.STROKE);
      pntLoc.setColor(Color.WHITE);
      pntLoc.setStrokeWidth(4);
      c.drawPath(pathArrow, pntLoc);
      pntLoc.setColor(Color.BLACK);
      pntLoc.setStrokeWidth(2);
      c.drawPath(pathArrow, pntLoc);
      c.restore();

      Log.i("MetaLoc", "Converting to B&W...");
      /* Here prepare a copy of black & white bitmap for display while filling watch's framebuffer */
      Bitmap bmpBW = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
      for (int i = 0; i < bmp.getWidth(); i++) {
        for (int j = 0; j < bmp.getHeight(); j++) {
          int clrNum = bmp.getPixel(i, j);
          float r = Color.red(clrNum), g = Color.green(clrNum), b = Color.blue(clrNum);
          int luma = (int) (r * 0.3 + g * 0.59 + b * 0.11);
          if (luma < 220) {
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
    } else {
      Log.i("MetaLoc", "Failed to get map, random wait.");
      sendUpdateMapMessage(new Random().nextInt(10) * 1000);
    }

    mHandler.sendEmptyMessageDelayed(2, 5000);
  }

  private static int MSG_UPDATE_MAP = 1;
  private Handler mHandler = new Handler() {
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case 1:
          updateMap();
          break;
        case 2:
          mNeedNewMap = true;
          mHandler.removeMessages(2);
          break;
      }
    }
  };

  /* drawing helpers */
  private Typeface mMetaWatchTypeface;
  private Typeface getMetaWatchTypeface()
  {
    if (mMetaWatchTypeface == null)
      mMetaWatchTypeface = Typeface.createFromAsset(getAssets(), "fonts/mw.ttf");

    return mMetaWatchTypeface;
  }

  private Paint mMapPaint;
  private Paint getMapPaint()
  {
    if (mMapPaint == null)
    {
      mMapPaint = new Paint();
      mMapPaint.setDither(true);
    }

    return mMapPaint;
  }

  private Paint mBarPaint;
  private Paint getBarPaint()
  {
    if (mBarPaint == null)
    {
      mBarPaint = new Paint();
      mBarPaint.setStyle(Paint.Style.FILL);
      mBarPaint.setColor(Color.BLACK);
    }

    return mBarPaint;
  }

  private Paint mInfoTextPaint;
  private Paint getInfoTextPaint(Paint.Align alignment)
  {
    if (mInfoTextPaint == null)
    {
      Typeface tf = getMetaWatchTypeface();
      mInfoTextPaint = new Paint();
      mInfoTextPaint.setTypeface(tf);
      mInfoTextPaint.setStyle(Paint.Style.FILL);
      mInfoTextPaint.setColor(Color.WHITE);
      mInfoTextPaint.setTextSize(8);
      mInfoTextPaint.setAntiAlias(true);
    }

    mInfoTextPaint.setTextAlign(alignment);
    return mInfoTextPaint;
  }

  private Paint mInfoDirectionArrowPaint;
  private Paint getInfoDirectionArrowPaint()
  {
    if (mInfoDirectionArrowPaint == null)
    {
      mInfoDirectionArrowPaint = new Paint();
      mInfoDirectionArrowPaint.setStyle(Paint.Style.STROKE);
      mInfoDirectionArrowPaint.setColor(Color.WHITE);
      mInfoDirectionArrowPaint.setStrokeWidth(3);
      mInfoDirectionArrowPaint.setAntiAlias(true);
    }

    return mInfoDirectionArrowPaint;
  }
}
