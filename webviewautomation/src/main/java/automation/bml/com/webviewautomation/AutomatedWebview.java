package automation.bml.com.webviewautomation;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import automation.bml.com.webviewautomation.RestAPI.DataModel.Actions;
import automation.bml.com.webviewautomation.RestAPI.DataModel.TransactionRequest;
import automation.bml.com.webviewautomation.RestAPI.DataModel.TransactionResponse;
import automation.bml.com.webviewautomation.RestAPI.RestAPI;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.content.Context.WIFI_SERVICE;

public class AutomatedWebview extends WebView
{
    private final String sharedPreferenceName = "BML_WEBVIEW_AUTOMATION";
    Context context;
    private int mnc, mcc;
    private String last_url;

    public AutomatedWebview(Context context) {
        super(context);
        this.context = context;
        init();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && canGoBack()) {
            goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }

    public void init()
    {
        setUUID(); // Setting the UUID on installation
        getSettings().setJavaScriptEnabled(true);
        //addJavascriptInterface(new AutoJavaScriptInterface(), "MYOBJECT");

        setWebChromeClient(new WebChromeClient());
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {

                return true;
            }
            public void onPageFinished(WebView view, String url) {
                //Checking 3G/4G

                injectJS();
                //String connectionType = getConnectionType();
                if (Connectivity.isConnectedWifi(context))
                {
                    Log.d("Connection Status: ", "Wifi");
                }

//                else if(Connectivity.isConnectedMobile(context))
//                {
                    Log.d("Connection Status: ", "3g/4g");
                    //changeWifiStatus(false);
//                    if(Connectivity.isConnectedMobile(context)) //If connected to 3G/4G
//                    {
//                        getMNCMCC();
//                        if(mnc != 0 || mcc != 0) //If MNC and MCC are not empty
//                        {
//                            TransactionRequest request = new TransactionRequest();
//
//                            // Setting the parameters for API call
//                            request.setAction("start");
//                            request.setMccmnc(String.valueOf(mcc)+String.valueOf(mnc));
//                            request.setInstall_id(getUUID());
//                            request.setApp_id(getUUID());
//                            request.setIp(getIPAddress());
//                            request.setUseragent(getUserAgent());
//
//                            //Calling the api
//                            try {
//                            OkHttpClient httpClient = new OkHttpClient.Builder().build();
//                            Retrofit retrofit = new Retrofit.Builder().addConverterFactory(GsonConverterFactory.create()).baseUrl(RestAPI.BASE_URL).client(httpClient).build();
//                            RestAPI service = retrofit.create(RestAPI.class);
//
//                            Call<TransactionResponse> meResponse = service.loadData(request);
//
//                                meResponse.enqueue(new Callback<TransactionResponse>() {
//                                    @Override
//                                    public void onResponse(Call<TransactionResponse> call, Response<TransactionResponse> response) {
//                                        if (response.isSuccessful()) {
//                                            TransactionResponse body = response.body();
//                                            Actions actions = body.getActions();
//                                            Map<String, String> params = actions.getParams();
//                                        }
//                                    }
//
//                                    @Override
//                                    public void onFailure(Call<TransactionResponse> call, Throwable t) {
//                                        t.printStackTrace();
//                                    }
//                                });
//                            }
//                            catch(Exception e)
//                            {
//                                e.printStackTrace();
//                            }
//                            }


//                    }
//                    else
//                    {
//
//                    }
                //}

            }
        });
    }
    // Automated actions
    public void wait(int seconds)
    {
        try {
            Thread.sleep(seconds*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void focus(String selector)
    {

    }
    public void enter(String text)
    {

    }
    public void click(String selector)
    {

    }
    public void takeScreenshot()
    {
        Picture picture = capturePicture();
        Bitmap b = Bitmap.createBitmap( picture.getWidth(),
                picture.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        picture.draw(c);
        FileOutputStream fos = null;
        try {

            fos = new FileOutputStream( "mnt/sdcard/yahoo.jpg" );
            if ( fos != null )
            {
                b.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void process(String action, String parameter)
    {
        if(action.equalsIgnoreCase("load"))
            loadUrl(parameter);

        else if(action.equalsIgnoreCase("wait")) {
            int seconds = 0;
            try {
                seconds = Integer.parseInt(parameter);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            wait(seconds);
        }
        else if(action.equalsIgnoreCase("focus")){
            focus(parameter);
        }
        else if(action.equalsIgnoreCase("enter")){
            enter(parameter);
        }
        else if(action.equalsIgnoreCase("click")){
            click(parameter);
        }
        else if(action.equalsIgnoreCase("screenshot")){
            takeScreenshot();
        }
    }

    // Processing functions

    private NetworkInfo getConnectionInfo()
    {
        NetworkInfo info = Connectivity.getNetworkInfo(getContext());
        return info;
    }

    private String getConnectionType()
    {
        return Connectivity.getNetworkInfo(getContext()).getTypeName();
    }

    public void changeWifiStatus(boolean status)
    {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(status);
    }

    private void getMNCMCC()
    {
        TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String networkOperator = tel.getNetworkOperator();

        if (!TextUtils.isEmpty(networkOperator)) {
            mcc = Integer.parseInt(networkOperator.substring(0, 3));
            mnc = Integer.parseInt(networkOperator.substring(3));
        }
    }

    public String getUserAgent()
    {
       return this.getSettings().getUserAgentString();
    }

    public String getIPAddress()
    {
        WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip;
    }

    public void setUUID()
    {
        SharedPreferences sharedPreferences = context.getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE);
        if(getUUID().isEmpty())
        {
            String newId = UUID.randomUUID().toString();
            SharedPreferences.Editor editor= sharedPreferences.edit();
            editor.putString("uuid", newId);
            editor.commit();
        }
    }

    public String getUUID()
    {
        SharedPreferences sharedPreferences = context.getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE);
        String uuid = sharedPreferences.getString("uuid","");
        return uuid;
    }

    // Miscellenous functions
    private void injectJS() {
        try {
            loadUrl("javascript:" + sb.toString());
            InputStream inputStream = getContext().getAssets().open("jquery.html");
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            String javascript_loader = Base64.encodeToString(buffer, Base64.NO_WRAP);
            String script = "location.href='https://www.apple.com'";
            String script2 = "history.goBack(-1)";
            //String encoded = Base64.encodeToString(script, Base64.NO_WRAP);
            //loadUrl("javascript:location.href('https://www.google.com');");

            StringBuilder sb = new StringBuilder();
            sb.append(javascript_loader);
            sb.append(script);
            //sb.append(script2);
            loadUrl("javascript:" + sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String fileNameGenerator()
    {
        return "";
    }

}