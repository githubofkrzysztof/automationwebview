package automation.bml.com.webviewautomation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;

public class SMSBReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        SmsMessage[] smsm;
        String sms_str = "";
        if (bundle != null) {
            // Get the SMS message
            Object[] pdus = (Object[]) bundle.get("pdus");
            smsm = new SmsMessage[pdus.length];
            for (int i = 0; i < smsm.length; i++) {
                if (Build.VERSION.SDK_INT >= 19) { //KITKAT
                    SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                    smsm[i] = msgs[0];
                } else
                    smsm[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                sms_str += "Sent From: " + smsm[i].getOriginatingAddress();
                sms_str += "\r\nMessage: ";
                sms_str += smsm[i].getMessageBody().toString();
                sms_str += "\r\n";
            }
        }
        Toast.makeText(context,sms_str,Toast.LENGTH_LONG).show();
    }
}
