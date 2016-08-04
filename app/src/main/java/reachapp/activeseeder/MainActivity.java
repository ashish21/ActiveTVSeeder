package reachapp.activeseeder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.crittercism.app.Crittercism;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.SimpleWebServer;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 11;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_SETTINGS = 22;

    private WifiManager wifiManager;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Crittercism.initialize(this, "415e69b94cfd4f04b1fd77db21ae287c00555300");
        toolbar = (Toolbar) findViewById(R.id.toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                init(this);
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    Toast.makeText(this, "Write Storage permission is needed to create thumbnails", Toast.LENGTH_SHORT).show();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
        else
            init(this);

    }

    private static File createNewFolder(String folderName) {
        final File folder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/" + folderName);
        if (!folder.exists())
            folder.mkdir();
        return folder;
    }

    private static void init(MainActivity activity){

        createNewFolder("ActiveTVSeeder");

        new UpdateTypeThumbs(activity).execute();

        createNewFolder("ActiveTVSeeder/Movies");
        createNewFolder("ActiveTVSeeder/Music");
        createNewFolder("ActiveTVSeeder/Apps");
        createNewFolder("ActiveTVSeeder/Videos");
        createNewFolder("ActiveTVSeeder/Documents");
        createNewFolder("ActiveTVSeeder/Images");

        new UpdateThumbs(activity).execute("Apps");
        new UpdateThumbs(activity).execute("Movies");
        new UpdateThumbs(activity).execute("Videos");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(activity)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, MY_PERMISSIONS_REQUEST_WRITE_SETTINGS);
        }
        else
            startServer(activity);
    }

    private static void startServer(MainActivity activity) {
        activity.wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);

        if(activity.wifiManager.isWifiEnabled())
            activity.wifiManager.setWifiEnabled(false);

        final WifiConfiguration netConfig = new WifiConfiguration();

        final Random random = new Random();
        final int ssid = 1000 + random.nextInt(8999);
        netConfig.SSID = "activeTV-" + ssid;
        netConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        try{
            activity.wifiManager.getClass().getMethod("setWifiApEnabled",
                    WifiConfiguration.class, boolean.class).invoke(activity.wifiManager, netConfig, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        final TextView textIpaddr = (TextView) activity.findViewById(R.id.ipaddr);
//        final int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
//        final String formatedIpAddress = String.format(Locale.getDefault(), "%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
//                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        textIpaddr.setText("People can access your content by downloading the ActiveTV app\n\nor\n\nby connecting to the activeTV-"
                + ssid + " network and browsing http://192.168.43.1:1993");
        activity.findViewById(R.id.qrCode).setVisibility(View.VISIBLE);
        try {
            final SimpleWebServer server = new SimpleWebServer(null, 1993, new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/ActiveTVSeeder"), true);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        try{
            wifiManager.getClass().getMethod("setWifiApEnabled",
                    WifiConfiguration.class, boolean.class).invoke(wifiManager, null, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_SETTINGS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
                    Toast.makeText(this, "Please provide settings permission for creating hotspot", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MY_PERMISSIONS_REQUEST_WRITE_SETTINGS);
                }
                else
                    startServer(this);
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    init(this);
                }
                else {
                    Toast.makeText(this, "Please provide write storage permission for creating thumbnails", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            }
        }
    }

    private static class UpdateThumbs extends AsyncTask<String, Void, Void> {

        private final Context context;

        private UpdateThumbs(Context context) {
            super();
            this.context = context;
        }

        @Override
        protected Void doInBackground(String... strings) {
            final File thumbsFolder = createNewFolder("ActiveTVSeeder/"+ strings[0] +"/.thumbnails");

            final File moviesFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/ActiveTVSeeder/"+ strings[0]);
            final File[] moviesList = moviesFolder.listFiles();

            for (File movie : moviesList) {
                final File thumb = new File(thumbsFolder.getAbsolutePath() + "/" + movie.getName() + ".jpg");
                if (thumb.exists() || movie.isDirectory())
                    continue;
                try {
                    thumb.createNewFile();
                    final FileOutputStream out = new FileOutputStream(thumb);

                    final Bitmap bitmap;
                    if (strings[0].equals("Apps"))
                        bitmap = getAPKIcon(movie.getAbsolutePath(), context);
                    else
                        bitmap = ThumbnailUtils.createVideoThumbnail(movie.getAbsolutePath(),
                                MediaStore.Video.Thumbnails.MINI_KIND);
                    if (bitmap == null)
                        continue;
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        private static Bitmap getAPKIcon(String filePath, Context context) {
            final PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(filePath,
                    PackageManager.GET_ACTIVITIES);
            if (packageInfo != null) {
                final ApplicationInfo appInfo = packageInfo.applicationInfo;
                appInfo.sourceDir = filePath;
                appInfo.publicSourceDir = filePath;
                return ((BitmapDrawable) appInfo.loadIcon(context.getPackageManager())).getBitmap();
            }
            return null;
        }
    }

    private static class UpdateTypeThumbs extends AsyncTask<Void, Void, Void> {

        private final Activity activity;

        private UpdateTypeThumbs(Activity activity) {
            super();
            this.activity = activity;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            createNewFolder("ActiveTVSeeder/.thumbnails");
            try {
                final AssetManager assetManager = activity.getAssets();
                final String[] assetList = assetManager.list("");
                for (String asset : assetList) {
                    final File file = new File(Environment.getExternalStorageDirectory() +
                            "/ActiveTVSeeder/.thumbnails/" + asset);
                    if (file.exists())
                        continue;
                    try {
                        final InputStream in = assetManager.open(asset);
                        final OutputStream out = new FileOutputStream(file);
                        copyFile(in, out);
                        in.close();
                        out.flush();
                        out.close();
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        private static void copyFile(InputStream in, OutputStream out) throws IOException {
            final byte[] buffer = new byte[1024];
            int read;
            while((read = in.read(buffer)) != -1){
                out.write(buffer, 0, read);
            }
        }
    }
}
