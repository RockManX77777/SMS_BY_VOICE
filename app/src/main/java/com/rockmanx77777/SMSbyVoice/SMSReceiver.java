package com.rockmanx77777.SMSbyVoice;

import java.util.Set;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * This receiver class gets a intercepts broadcasts from the system when an SMS is received.
 * It then sends a local broadcast so that the SMSBuVoice activity can utilize it.
 * @author Johnny Cardenas
 *
 */
public class SMSReceiver extends BroadcastReceiver{

	public SMSReceiver(){
		
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("MSG", "Receiver's onReceived Called");
		if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")){
			//Get the SMS message passed in
			Bundle bundle = intent.getExtras();        
			SmsMessage[] msgs = null;
			String senderNumber = "";
			String body = "";
			if (bundle != null){
				//Retrieve the SMS message received
				Object[] pdus = (Object[]) bundle.get("pdus");
				msgs = new SmsMessage[pdus.length];            
				for (int i=0; i<msgs.length; i++){
					msgs[i] = SmsMessage.createFromPdu((byte[])pdus[i]);  
					body = msgs[i].getMessageBody().toString();    
				}
				senderNumber = msgs[0].getOriginatingAddress();

				Intent localSMS = new Intent(context, SMSByVoice.class);
				localSMS.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				localSMS.setAction("com.rockmanx77777.SMSByVoice.PROCESS");
				localSMS.putExtra("senderNumber", senderNumber);
				localSMS.putExtra("body", body);
				Log.d("MSG", "Starting activity from Receiver");
				context.startActivity(localSMS);
			}
		}
		else{
			Log.d("MSG", "intent.getAction() !!!!!= android.provider.Telephony.SMS_RECEIVED");
		}

	}

}
