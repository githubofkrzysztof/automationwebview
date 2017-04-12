package automation.bml.com.webviewautomation;

import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;

import automation.bml.com.webviewautomation.RestAPI.DataModel.TransactionRequest;
import automation.bml.com.webviewautomation.RestAPI.DataModel.TransactionResponse;
import automation.bml.com.webviewautomation.RestAPI.RestAPI;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.WIFI_SERVICE;

public class AutomatedWebview extends WebView
{
    Context context;
    private int mnc, mcc;
    private String userAgent = "Android", ipAddress;
    private NetworkInfo info;

    public AutomatedWebview(final Context context) {
        super(context);
        this.context = context;
        //getUserAgent();

        ipAddress = getIPAddress();
        Log.d("IpAddress", ipAddress);
        getMNCMCC();
        getSettings().setJavaScriptEnabled(true);
        addJavascriptInterface(new AutoJavaScriptInterface(), "MYOBJECT");

        setWebChromeClient(new WebChromeClient());
        setWebViewClient(new WebViewClient() {

            public void onPageFinished(WebView view, String url) {

                //Checking 3G/4G
                info = getConnectionInfo();

                injectJS();
                TransactionRequest request = new TransactionRequest();
                OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder().addHeader("api-token", getSharedPreferences("MyPreference", MODE_PRIVATE).getString("token", "")).build();
                        return chain.proceed(request);
                    }
                }).build();
                Retrofit retrofit = new Retrofit.Builder().addConverterFactory(GsonConverterFactory.create()).baseUrl(RestAPI.BASE_URL).client(httpClient).build();
                RestAPI service = retrofit.create(RestAPI.class);
                Call<TransactionResponse> meResponse = service.loadData(request);
                meResponse.enqueue(new Callback<TransactionResponse>() {
                    @Override
                    public void onResponse(Call<TransactionResponse> call, Response<TransactionResponse> response) {
                        if (response.isSuccessful()) {
                            TransactionResponse body = response.body();

                        }
                    }

                    @Override
                    public void onFailure(Call<TransactionResponse> call, Throwable t) {

                    }


                });
            }
        });
    }
    public void init()
    {
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
    private void getUserAgent()
    {
        userAgent = this.getSettings().getUserAgentString();
    }
    private NetworkInfo getConnectionInfo()
    {
        NetworkInfo info = Connectivity.getNetworkInfo(getContext());
        return info;
    }

    private String getIPAddress()
    {
        WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip;
    }
    private void setUserAgent()
    {
        getSettings().setUserAgentString(userAgent);
    }
    private void injectJS() {
        try {
//
//            InputStream inputStream = context.getAssets().open("jscript.js");
//            byte[] buffer = new byte[inputStream.available()];
//            inputStream.read(buffer);
//            inputStream.close();
//            String encoded = Base64.encodeToString(buffer, Base64.NO_WRAP);
//            loadUrl("javascript:alert('abc');");

            StringBuilder sb = new StringBuilder();
            sb.append("alert('abc');");

            loadUrl("javascript:" + sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}