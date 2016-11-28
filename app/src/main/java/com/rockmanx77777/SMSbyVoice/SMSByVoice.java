package com.rockmanx77777.SMSbyVoice;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

;

public class SMSByVoice extends Activity implements OnClickListener,
TextWatcher, OnCheckedChangeListener {

	final private String MSG = "MSG";

	/**
	 * Used to capture user voice input when pressing the Speak Button
	 */
	final private String STRING_SAY_MESSAGE = "Say your message.";

	/**
	 * Used to read the message heard back to the user
	 */
	final private String STRING_YOUR_MESSAGE_IS = "Your message is: ";

	/**
	 * Used to indicate that the message has been sent
	 */
	final private String STRING_MESSAGE_SENT = "Message sent.";

	/**
	 * Used to indicate that the message has been delivered
	 */
	final private String STRING_MESSAGE_DELIVERED = "Message delivered.";

	/**
	 * Used to indicate that the message failed to send
	 */
	final private String STRING_MESSAGE_SENDING_FAILED = "Message sending failed.";

	/**
	 * Used right before start reading the sender name or address
	 */
	final private String STRING_NEW_MESSAGE = "New message from: ";

	/**
	 * Used right before reading an incoming SMS
	 */
	final private String STRING_THE_MESSAGE_IS = "The message is: ";

	/**
	 * Used to ask the user if they want to reply to the SMS just read.
	 */
	final private String STRING_SEND_REPLY_PROMPT = "Would you like to send a reply?";

	/**
	 * Used to confirm that the user wants to send the reply after hearing it.
	 */
	final private String STRING_FINAL_SEND_PROMPT = "Would you like to send this message?";

	private ToggleButton backgroundButton;
	private Button speakOutButton;
	private Button speakInButton;
	private Button clearAllButton;
	private Button sendReplyButton;
	private TextView textViewField;
	private EditText userTextBox;
	private AdView adView;

	private String textWritten = "";
	private String senderNumber = "";
	private String body = "";
	private String senderName = "";

	private boolean serviceRunning = false;
	private boolean registered = false;
	private boolean waitingOnResult = true;
	private boolean ranFromBackground = false;
	private	boolean recreatedToTalk = false;

	private TextToSpeech tts;

	/**
	 * Result will be used to confirm TSS is installed and working
	 */
	final private int INT_TTS_CHECK = 775;

	/**
	 * Result will become the SMS to send.
	 */
	final private int INT_MESSAGE_DICTATION = 336;

	/**
	 * Result will decide whether or not to send a reply
	 */
	final private int INT_ASK_REPLY = 65;

	/**
	 * Reply will decide whether or not to send the reply
	 */
	final private int INT_ASK_SEND = 7;

	private SMSReceiver bReceiver = new SMSReceiver();

	PhoneStateListener phoneStateListener = new PhoneStateListener() {

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if(state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK){
				Log.d("MSG", "Pausing Service");
				unregisterReceiver();
				try{
					if(tts.isSpeaking()){
						tts.stop();
					}
				}
				catch(NullPointerException n){
					Log.d(MSG,  "TTS not initialized");
				}

			}
			else{
				Log.d("MSG", "Resuming Service");
				registerReceiver();
			}
		}

	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(MSG, "onCreate started");
		this.checkTTSLibraryPresent();

		super.onCreate(savedInstanceState);
		serviceRunning = isServiceRunning();

		//Register to listen for callsS
		phoneListener(true);
		// Create Layouts and Views only. Assign IDs and such to make them usable
		setContentView(R.layout.activity_sms_by_voice);

		// Create the adView
		MobileAds.initialize(getApplicationContext(), "ca-app-pub-8246648481142727~2125264898");
		this.adView = (AdView) (findViewById(R.id.adView));

		// Initiate a generic request to load it with an ad
		this.adView.loadAd(new AdRequest.Builder().build());

		// BUTTONS
		this.speakOutButton = (Button) (findViewById(R.id.speech_out_button));
		this.speakInButton = (Button) (findViewById(R.id.speech_in_button));
		this.clearAllButton = (Button) (findViewById(R.id.clear_button));
		this.sendReplyButton = (Button) (findViewById(R.id.send_reply));

		// TOOGLE BUTTONS
		this.backgroundButton = (ToggleButton) (findViewById(R.id.background_button));

		// TEXT object
		this.userTextBox = (EditText) (findViewById(R.id.user_text));
		this.textViewField = (TextView) (findViewById(R.id.text_view));

		//Called from Service
		if(getIntent().getAction().equals("com.rockmanx77777.SMSByVoice.PROCESS")){
			ranFromBackground = true;
			Log.d(MSG, "Not Creating default");
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

			senderNumber = getIntent().getExtras().getString("senderNumber");

			if(senderNumber.equals("")){
				Log.d(MSG, "Empty intent");
			}
			else{
				body = getIntent().getExtras().getString("body");
				Log.d(MSG, "body = " + body);
				senderName = getContactName(senderNumber);

				String str = "";

				if(senderName.equals("")){
					str += "SMS from " +  senderNumber + ":\n" + body + "\n";
				}
				else{
					str += "SMS from " +  senderName + ":\n" + body + "\n";
				}

				//Update textViewField
				textViewField.setText(str);

				//Enable listen button
				speakOutButton.setEnabled(true);

				//Enable send button if text already entered
				if(userTextBox.getText().length()>0){
					sendReplyButton.setEnabled(true);
				}
			}
		}
		else{
			Log.d(MSG, "Creating default");
			//Default Screen
			this.sendReplyButton.setEnabled(false);
			this.speakOutButton.setEnabled(false);
		}

		this.backgroundButton.setChecked(serviceRunning);

		if(!this.isVoiceRecognizerPresent()){
			this.speakInButton.setText("Recognizer Not Present");
			this.speakInButton.setEnabled(false);
			//Prompt to install recognizer
			Intent installTTSIntent = new Intent();
			installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
			waitingOnResult = true;
			startActivity(installTTSIntent);
		}

		this.updateVariables();

		this.addListeners();
		if(getIntent().getAction().equals("com.rockmanx77777.SMSByVoice.PROCESS")){
			if((getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0){
				Log.d(MSG, "Not Launched from history.");
				recreatedToTalk = true;
			}
			else{
				recreatedToTalk = false;
				Log.d(MSG, "Launched from history");
			}
		}
		Log.d(MSG, "onCreate ended");
	}

	/**
	 * Return the name of the contact that matches the passed phone number. If there's no match. It returns an empty string.
	 * @param number The phone number to look up
	 * @return the same of the contact or empty string if no match
	 */
	@SuppressWarnings("deprecation")
	protected String getContactName(String number) {
		//Define columns for query to return
		String[] columns = new String[] {ContactsContract.PhoneLookup.DISPLAY_NAME,	ContactsContract.PhoneLookup._ID};

		//Get the URI for the contact
		Uri lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(senderNumber));

		Cursor cursor;
		String name ="";

		if(android.os.Build.VERSION.SDK_INT < 11){
			//Pre-Honeycomb
			cursor = managedQuery(lookupUri, null, null, null, null);
		}
		else{
			//Query
			cursor = this.getContentResolver().query(lookupUri, columns, null, null, null);
		}

		if(cursor.moveToFirst()){
			name =  cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
		}
		return name;
	}

	@Override
	protected void onStart() {
		Log.d(MSG, "onStart started");
		super.onStart();
		Log.d(MSG, "onStart ended");
	}

	@Override
	protected void onResume() {
		Log.d(MSG, "onResume started");
		if(!serviceRunning){
			registerReceiver();
			phoneListener(true);
		}
		super.onResume();
		Log.d(MSG, "onResume ended");
	}



	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		Log.d(MSG, "WinFocCha");
		if(!hasFocus){
			if(!waitingOnResult && serviceRunning){
				Log.d(MSG,"Toast from pause");
				showToast("Listening for incoming messages", Toast.LENGTH_LONG);
			}
		}
	}

	@Override
	protected void onPause() {
		Log.d(MSG, "onPause started");

		stopSpeaking();

		unregisterReceiver();
		phoneListener(false);

		super.onPause();
		Log.d(MSG, "onPause ended");
	}


	@Override
	protected void onStop() {
		Log.d(MSG, "onStop started");

		super.onStop();
		Log.d(MSG, "onStop ended");
	}

	@Override
	protected void onDestroy() {
		Log.d(MSG, "onDestroy started");

		Log.d(MSG, "shutting down");
		if(tts != null){
			tts.shutdown();
		}
		unregisterReceiver();
		super.onDestroy();
		Log.d(MSG, "onDestroy ended");
	}


	@Override
	public void onBackPressed() {
		Log.d(MSG, "onBackPressed started");
		//If not running in background, but user entered text, do not close either
		if(!(this.userTextBox.getText().length() < 1)){
			moveTaskToBack(false);
			Log.d(MSG, "Not Terminating Activity. Data entered");
		}

		else{
			if(tts!=null){
				Log.d(MSG, "shutting down");
				tts.shutdown();
			}
			Log.d(MSG, "About to finish from onBackPressed");
			finish();
			super.onBackPressed();
		}
		Log.d(MSG, "onBackPressed exiting");
	}



	private void addListeners(){
		clearAllButton.setOnClickListener(this);
		sendReplyButton.setOnClickListener(this);
		userTextBox.addTextChangedListener(this);
		backgroundButton.setOnCheckedChangeListener(this);
		speakInButton.setOnClickListener(this);
		speakOutButton.setOnClickListener(this);
	}

	/**
	 * Assign all view-dependent variables their values based on
	 * the states and contents of Views and Buttons
	 */
	private void updateVariables(){
		Log.d(MSG, "updateVariables started");
		//TODO Add SMS related variables
		this.textWritten = this.userTextBox.getText().toString();
		Log.d(MSG, "updateVariables ended");
	}


	/**Checks to see if recognizer for voice input is present
	 *
	 * @return whether or not voice input is present
	 */
	private boolean isVoiceRecognizerPresent(){

		Log.d(MSG, "isRecognizerPresent started");
		PackageManager pm = getPackageManager();

		List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
		Log.d(MSG, "isRecognizerPresent ending");
		if (activities.size() != 0) {
			return true;
		}
		return false;
	}


	/**Starts and Activity to check for Text-To-Speech libraries
	 *
	 */
	private void checkTTSLibraryPresent(){
		Log.d(MSG, "About to start checking tts");
		waitingOnResult = true;
		startActivityForResult(new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
		, INT_TTS_CHECK);
	}


	public void onClick(View v) {
		// TODO Auto-generated method stub
		int id = v.getId();
		if(id == speakOutButton.getId()){
			tts.speak(this.textViewField.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
		}

		//User Speaks
		else if(id == speakInButton.getId()){
			this.textWritten = textViewField.getText().toString();
			HashMap<String, String> myHash = new HashMap<String, String>();
			myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, STRING_SAY_MESSAGE);
			tts.speak(this.STRING_SAY_MESSAGE, TextToSpeech.QUEUE_FLUSH, myHash);
		}

		//Clear everything
		else if(id == clearAllButton.getId()){
			textViewField.setText("");
			userTextBox.setText("");
			senderNumber = "";
			senderName = "";
			body = "";
			this.speakOutButton.setEnabled(false);
			this.sendReplyButton.setEnabled(false);
			this.sendReplyButton.setText("Send Reply");
		}

		//Send typed message
		else if(id == sendReplyButton.getId()){
			sendReplyButton.setText("Sending...");
			sendReplyButton.setEnabled(false);
			tts.speak("Sending message", TextToSpeech.QUEUE_FLUSH, null);
			sendSMS(senderNumber, userTextBox.getText().toString());
		}
	}

	public void afterTextChanged(Editable e) {
		// TODO Auto-generated method stub
		if(e.length()<1){
			this.sendReplyButton.setEnabled(false);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// TODO Auto-generated method stub

	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {
		// TODO Auto-generated method stub
		if(!this.sendReplyButton.isEnabled() && senderNumber!=""){
			sendReplyButton.setEnabled(true);
		}
	}

	public void onCheckedChanged(CompoundButton toogleButton, boolean checked) {
		Log.d(MSG, "onCheckedChanged called");
		if(checked){
			serviceRunning = true;
			unregisterReceiver();
			startService(new Intent(this, SMSService.class));
		}
		else{
			stopService(new Intent(this, SMSService.class));
			registerReceiver();
			serviceRunning = false;
		}
		Log.d(MSG, "onCheckedChanged finished");
	}

	@TargetApi(15)
	@SuppressWarnings("deprecation")
	//@TargetApi(15)
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(MSG,"OnActivityResultCalled.");
		Log.d(MSG, "requestCode = " + requestCode);
		Log.d(MSG, "resultCode = " + resultCode);
		// TODO Auto-generated method stub

		//CHECKED TO SEE IF SPEECH RECOGNIZER IS INSTALLED
		if (requestCode == this.INT_TTS_CHECK){
			//Set ttsAvailable to true if TTS is available. Else prompt to install
			if(Build.VERSION.SDK_INT < 16){//Up to and including ICS
				Log.d(MSG, "Not Jelly Bean");
				Log.d(MSG, "Request code = " + requestCode);
				if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS){
					//Initialize TTS
					Log.d(MSG, "Init TTS");
					tts = new TextToSpeech(this, new OnInitListener(){

						public void onInit(int status) {
							if(status != TextToSpeech.SUCCESS){
								speakOutButton.setText("TTS not available");
								speakOutButton.setEnabled(false);
							}
							else{
								int a = tts.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener(){

									public void onUtteranceCompleted(String utteranceId) {
										Log.d(MSG, "OnUtteranceCompleted started");
										Log.d(MSG, "Finished Speaking. ID: " + utteranceId);

										//Finished saying "Would you like to send a reply"
										if (utteranceId.equals(STRING_SEND_REPLY_PROMPT)){
											Intent promptReply = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
											promptReply.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
											promptReply.putExtra(RecognizerIntent.EXTRA_PROMPT, "Reply?");
											promptReply.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
											waitingOnResult = true;
											startActivityForResult(promptReply, INT_ASK_REPLY);
										}

										//Finished saying "Say your message"
										else if (utteranceId.equals(STRING_SAY_MESSAGE)){
											Intent recogSpeech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
											recogSpeech.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
											recogSpeech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
											recogSpeech.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your message");
											recogSpeech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
											waitingOnResult = true;
											startActivityForResult(recogSpeech, INT_MESSAGE_DICTATION);
										}

										//Finished saying "Would you like to send the message?"
										else if(utteranceId.equals(STRING_FINAL_SEND_PROMPT)){
											Intent conf = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
											conf.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
											waitingOnResult = true;
											startActivityForResult(conf, INT_ASK_SEND);
										}
										//Finished saying "Message sent"
										else if(utteranceId.equals(STRING_MESSAGE_SENT)){
											if(!ranFromBackground){
												PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
												PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
												wl.acquire(1);
												clearAllButton.performClick();
												moveTaskToBack(false);
											}
										}

										else{
											Log.d(MSG, "Some other utterace Id: " + utteranceId);
										}
									}

								}

										);

								Log.d(MSG, "SetLstener result = "+a);

								if(recreatedToTalk){
									processMessage();
								}
							}
						}

					});
					Log.d(MSG, "About to set uteranceCompleted");
					if(recreatedToTalk){
						processMessage();
					}
				}

				else{//TTS != PASS
					waitingOnResult = true;
					showToast("Please install a Text-To-Speech Engine", Toast.LENGTH_LONG);
					startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
				}
			}//End if SDK

			else{
				Log.d(MSG, "Jelly Bean");
				tts = new TextToSpeech(this, new OnInitListener(){

					public void onInit(int status) {
						int n = tts.isLanguageAvailable(Locale.getDefault());
						if(n ==	TextToSpeech.LANG_AVAILABLE || n == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
								n == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE){
							Log.d(MSG, "TTS available");

							//IF JELLY BEAN BREAKS, it's because of this
							tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){

								@Override
								public void onDone(String utteranceId) {
									Log.d(MSG, "Finished Speaking. ID: " + utteranceId);

									//Finished saying "Would you like to send a reply"
									if (utteranceId.equals(STRING_SEND_REPLY_PROMPT)){
										Intent promptReply = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
										promptReply.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
										promptReply.putExtra(RecognizerIntent.EXTRA_PROMPT, "Reply?");
										promptReply.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
										waitingOnResult = true;
										startActivityForResult(promptReply, INT_ASK_REPLY);
									}

									//Finished saying "Say your message"
									else if (utteranceId.equals(STRING_SAY_MESSAGE)){
										Intent recogSpeech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
										recogSpeech.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
										recogSpeech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
										recogSpeech.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your message");
										recogSpeech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
										waitingOnResult = true;
										startActivityForResult(recogSpeech, INT_MESSAGE_DICTATION);
									}

									//Finished saying "Would you like to send the message?"
									else if(utteranceId.equals(STRING_FINAL_SEND_PROMPT)){
										Intent conf = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
										conf.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
										waitingOnResult = true;
										startActivityForResult(conf, INT_ASK_SEND);
									}
									//Finished saying "Message sent"
									else if(utteranceId.equals(STRING_MESSAGE_SENT)){
										if(!ranFromBackground){
											PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
											PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
											wl.acquire(1);
											moveTaskToBack(false);
										}
									}

									else{
										Log.d(MSG, "Some other utterace Id: " + utteranceId);
									}
								}
								@Override
								public void onError(String utteranceId) {
									Log.d(MSG, "Error Speaking");

								}

								@Override
								public void onStart(String utteranceId) {
									Log.d(MSG, "About to start speaking");
								}
							});
							if(recreatedToTalk){
								processMessage();
							}
						}
						else{
							speakOutButton.setText("TTS not available");
							speakOutButton.setEnabled(false);
						}
					}

				});

			}//End Else Jelly Bean
		}//END CHECKED TO SEE IF SPEECH RECOGNIZER IS INSTALLED

		//USER ANSWERED WHETHER OR NOT TO SEND A REPLY
		else if(requestCode == INT_ASK_REPLY){
			if(resultCode == Activity.RESULT_OK){
				if(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0).equals("yes")){
					HashMap<String, String> myHash = new HashMap<String, String>();
					myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, STRING_SAY_MESSAGE);
					tts.speak(STRING_SAY_MESSAGE, TextToSpeech.QUEUE_FLUSH, myHash);
				}
				else if(!this.registered){
					Log.d(MSG, "User did not say yes, moving to back");
					this.clearAllButton.performClick();
					PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
					PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
					wl.acquire(1);
					moveTaskToBack(false);
				}
			}
			else{
				//User did not want to send a reply
				if(!this.registered){
					Log.d(MSG, "User did not want to send reply Moving to back");
					this.clearAllButton.performClick();
					PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
					PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
					wl.acquire(1);
					moveTaskToBack(false);
				}
			}
		}//END USER ANSWERED WHETHER OR NOT TO SEND A REPLY


		//USER SPOKE WHAT WILL BECOME THE SMS TO SEND
		else if (requestCode == INT_MESSAGE_DICTATION){
			if(resultCode == Activity.RESULT_OK){

				int start = userTextBox.getSelectionStart();
				int end = userTextBox.getSelectionEnd();
				String speechHeard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);

				userTextBox.getText().replace(Math.min(start, end), Math.max(start, end),
						speechHeard, 0, speechHeard.length());

				userTextBox.invalidate();

				textWritten = userTextBox.getText().toString();

				//Read out current SMS to user
				tts.speak(this.STRING_YOUR_MESSAGE_IS + " " + userTextBox.getText(), TextToSpeech.QUEUE_FLUSH, null);

				if(senderNumber != ""){//Prompt to send reply if there's a number to send it to
					HashMap<String,String> myHash = new HashMap<String,String>();
					myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, STRING_FINAL_SEND_PROMPT);
					tts.speak(STRING_FINAL_SEND_PROMPT, TextToSpeech.QUEUE_ADD, myHash);
				}
			}
			else{
				Log.d(MSG, "MESSAGE DICTATION NOT OK: resultCode = " + resultCode);
				if(!registered){
					Log.d(MSG, "Moving to back");
					this.clearAllButton.performClick();
					PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
					PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
					wl.release();
					moveTaskToBack(false);
				}
			}

		}//USER SPOKE WHAT WILL BECOME THE SMS TO SEND

		//USER SPOKE WHETHER OR NOT TO SEND THE SMS
		else if(requestCode == INT_ASK_SEND){

			if(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0).equals("yes")){
				sendReplyButton.setText("Sending...");
				sendReplyButton.setEnabled(false);
				tts.speak("Sending message", TextToSpeech.QUEUE_FLUSH, null);
				this.sendSMS(this.senderNumber, textWritten);
			}
			else{
				Log.d(MSG, "User did not want to send SMS");
				if(!registered){
					clearAllButton.performClick();
					PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
					PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
					wl.release();
					moveTaskToBack(false);
				}
			}
		}
		else{
			Log.d(MSG, "Some other request code: " + requestCode);
		}
		waitingOnResult = false;

	}


	public void onUtteranceCompleted(String utteranceId) {

		Log.d(MSG, "OnUtteranceCompleted completed");
	}

	@Override
	protected void onNewIntent(Intent intent) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

		Log.d(MSG,"onNewIntentCalled");
		if(!serviceRunning){
			registerReceiver();
		}

		if(intent.getAction().equals("com.rockmanx77777.SMSByVoice.PROCESS")){
			ranFromBackground = true;
			senderNumber = intent.getExtras().getString("senderNumber");
			if(senderNumber.equals("")){
				Log.d(MSG, "Empty intent");
			}
			else{
				body = intent.getExtras().getString("body");
				senderName = getContactName(senderNumber);

				String str = "";
				if(senderName.equals("")){
					str += "SMS from " +  senderNumber + ":\n" + body + "\n";
				}
				else{
					str += "SMS from " +  senderName + ":\n" + body + "\n";
				}

				//Update textViewField
				textViewField.setText(str);

				//Enable listen button
				speakOutButton.setEnabled(true);

				//Enable send button if text already entered
				if(userTextBox.getText().length()>0){
					sendReplyButton.setEnabled(true);
				}
			}
			processMessage();
		}
		else{
			Log.d(MSG,"Not doing anything");
		}
	}

	/**
	 * Announce message arrival and speak it.
	 */
	private void processMessage() {
		Log.d(MSG, "processMessage called");

		//Speak sender
		if(senderName.equals("")){
			tts.speak(this.STRING_NEW_MESSAGE + senderNumber, TextToSpeech.QUEUE_FLUSH, null);
		}
		else{
			tts.speak(STRING_NEW_MESSAGE + senderName, TextToSpeech.QUEUE_FLUSH, null);
		}

		try{
		this.markAsRead(body);
		}
		catch(Exception e){
			Log.d(MSG, "Could not mark as read");
		}

		//Speak body
		tts.speak(this.STRING_THE_MESSAGE_IS + this.body, TextToSpeech.QUEUE_ADD, null);

		//Ask if the user wants to reply
		HashMap<String, String> myHash = new HashMap<String, String>();
		myHash.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, STRING_SEND_REPLY_PROMPT);
		tts.speak(this.STRING_SEND_REPLY_PROMPT, TextToSpeech.QUEUE_ADD, myHash);
	}


	private void sendSMS(String phoneNumber, String message){
		//TODO divide message if needed.
		final String SENT = "SMS_SENT";
		final String DELIVERED = "SMS_DELIVERED";
		PendingIntent sentPI = PendingIntent.getBroadcast(this, 0,
				new Intent(SENT), 0);
		PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0,
				new Intent(DELIVERED), 0);

		//When the SMS has been sent
		registerReceiver(new BroadcastReceiver(){
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				switch (getResultCode()){
				case Activity.RESULT_OK:
					clearAllButton.performClick();

					HashMap<String, String> m = new HashMap<String, String>();
					m.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, STRING_MESSAGE_SENT);
					tts.speak(STRING_MESSAGE_SENT, TextToSpeech.QUEUE_FLUSH, m);
					break;

				default:
					sendReplyButton.setText("Send Reply");
					sendReplyButton.setEnabled(true);
					tts.speak(STRING_MESSAGE_SENDING_FAILED,TextToSpeech.QUEUE_FLUSH, null);
				}
			}

		}, new IntentFilter(SENT));
		//When the SMS has been delivered
		registerReceiver(new BroadcastReceiver(){
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				switch (getResultCode()){
				case Activity.RESULT_OK:
					clearAllButton.performClick();
					tts.speak(STRING_MESSAGE_DELIVERED, TextToSpeech.QUEUE_FLUSH, null);
					break;
				default:
					sendReplyButton.setText("Send Reply");
					sendReplyButton.setEnabled(true);
					tts.speak(STRING_MESSAGE_SENDING_FAILED,TextToSpeech.QUEUE_FLUSH, null);
					sendReplyButton.setEnabled(true);
					break;
				}
			}
		}, new IntentFilter(DELIVERED));
		SmsManager sms = SmsManager.getDefault();
		sms.sendTextMessage(senderNumber, null, message, sentPI, deliveredPI);

		this.saveToSent(senderNumber, message);
	}


	/**
	 * Shows toast with specified message and duration
	 * @param string Text to show
	 * @param duration duration as Toast.LENGHT_****
	 */
	private void showToast(String text, int duration) {
		Toast toast = Toast.makeText(this, text, duration);
		toast.show();
	}

	/**
	 * Mask the incoming message as read in the Messaging App. May throw and expection
	 * @param body the message containing this body will be marked as read
	 */
	private void markAsRead(String body){
		Uri inboxTable = Uri.parse("content://sms/inbox");
		ContentValues vals = new ContentValues();
		vals.put("read", true);
		vals.put("seen", true);
		String clause = "body='"+body+"'";
		getContentResolver().update(inboxTable, vals, clause, null);
	}


	/**
	 * Saves the message sent to the Messaging app conversation. May throw an exception
	 * @param phoneNumber the number replied to
	 * @param message the reply sent
	 */
	private void saveToSent(String phoneNumber, String message){
		ContentValues values = new ContentValues();
		values.put("address", phoneNumber);
		values.put("body", message);
		// Note: This uses an Android internal API to save to Sent-folder
		getContentResolver().insert(Uri.parse("content://sms/sent"), values);
	}

	/**
	 * Implemented from geekQ from stackoverflow.com
	 * @return Whether the service is running or not
	 */
	private boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (SMSService.class.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private void unregisterReceiver(){
		try{
			Log.d(MSG, "unregistering");
			unregisterReceiver(bReceiver);
		}
		catch (IllegalArgumentException i){
			Log.d("MSG", "Not registered");
		}
	}

	private void registerReceiver(){
		try{
			Log.d(MSG, "registering");
			registerReceiver(bReceiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
		}
		catch (IllegalArgumentException i){
			Log.d("MSG", "Already registered");
		}
	}

	/**
	 * Turns the phone state listener on or off
	 * @param listen whether to listen for CALL_STATE (true) or NONE (false)
	 */
	private void phoneListener(boolean listen){
		TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		if(mgr != null) {
			if (listen){
				mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
			}

			else{
				mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
			}

		}
	}


	private void stopSpeaking(){
		try{
				tts.stop();
		}
		catch(NullPointerException n){
			Log.d(MSG, "TTS = null");
		}
	}
}