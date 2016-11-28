package com.rockmanx77777.SMSbyVoice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;


public class SMSService extends Service{

	SMSReceiver receiver = new SMSReceiver();

	PhoneStateListener phoneStateListener = new PhoneStateListener() {

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if(state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK){
				Log.d("MSG", "Pausing Service");
				try{
					unregisterReceiver(receiver);
				}
				catch (IllegalArgumentException i){
					Log.d("MSG", "Not registered");
				}
			}
			else{
				Log.d("MSG", "Resuming Service");
				try{
					registerReceiver(receiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
				}
				catch (IllegalArgumentException i){
					Log.d("MSG", "Already registered");
				}
			}
		}

	};
	public int onStartCommand(Intent intent, int flags, int startId){
		Log.d("MSG", "SMSService onStartCommand Started");

		TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		if(mgr != null) {
			mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		}
		createNotification();
		this.registerReceiver(receiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
		super.onCreate();
		Log.d("MSG", "SMSService onStartCommand Ended");
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	
	
	@Override
	public void onDestroy() {
		Log.d("MSG", "Pausing Service");
		try{
			unregisterReceiver(receiver);
		}
		catch (IllegalArgumentException i){
			Log.d("MSG", "Not registered");
		}
	}

	private void createNotification(){
		Log.d("MSG","createNotification Started");

		final int NOTIFICATION_ID = 77777;

		Intent intent = new Intent(this, SMSByVoice.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("senderNumber", "");
		intent.setAction("com.rockmanx77777.SMSByVoice.RESUME");

		PendingIntent pi = PendingIntent.getActivity(this, 1, intent, 0);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

		builder.setContentTitle("SMS By Voice");
		builder.setContentText("Incoming SMS messages will be spoken");
		builder.setSmallIcon(R.drawable.ic_launcher_sbv_small);
		builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_sbv_small));
		builder.setContentIntent(pi);
		Notification notification = builder.getNotification();

		notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT;

		startForeground(NOTIFICATION_ID, notification);

		Log.d("MSG","createNotification Ended");
	}
}
;