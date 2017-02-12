package com.segway.speechdemo;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.voice.Languages;
import com.segway.robot.sdk.voice.Recognizer;
import com.segway.robot.sdk.voice.Speaker;
import com.segway.robot.sdk.voice.VoiceException;
import com.segway.robot.sdk.voice.audiodata.RawDataListener;
import com.segway.robot.sdk.voice.grammar.GrammarConstraint;
import com.segway.robot.sdk.voice.grammar.Slot;
import com.segway.robot.sdk.voice.recognition.RecognitionListener;
import com.segway.robot.sdk.voice.recognition.RecognitionResult;
import com.segway.robot.sdk.voice.recognition.WakeupListener;
import com.segway.robot.sdk.voice.recognition.WakeupResult;
import com.segway.robot.sdk.voice.tts.TtsListener;


import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.locomotion.sbv.AngularVelocity;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.locomotion.sbv.LinearVelocity;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Bundle;
import android.app.Activity;
import android.widget.TextView;

import org.w3c.dom.Text;

import static android.R.id.message;
import static android.content.ContentValues.TAG;
import static android.graphics.PorterDuff.Mode.CLEAR;
import static com.segway.speechdemo.R.string.tips;

public class MainActivity extends Activity implements View.OnClickListener{

    Server server;
    TextView  msg, status;

    // Robot
    private static final String TAG = "MainActivity";
    private static final String FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
    private static final int SHOW_MSG = 0x0001;
    private static final int APPEND = 0x000f;
    private static final int CLEAR = 0x00f0;
    private ServiceBinder.BindStateListener mRecognitionBindStateListener;
    private ServiceBinder.BindStateListener mSpeakerBindStateListener;
    private boolean isBeamForming = false;
    private boolean bindSpeakerService;
    private boolean bindRecognitionService;
    private int mSpeakerLanguage;
    private int mRecognitionLanguage;
    private Button mBindButton;
    private Button mUnbindButton;
    public Button mSpeakButton;
    private Button mStopSpeakButton;
    private Button mStartRecognitionButton;
    private Button mStopRecognitionButton;
    private Button mBeamFormListenButton;
    private Button mStopBeamFormListenButton;
    private Switch mEnableBeamFormSwitch;
    private TextView mStatusTextView;
    private Recognizer mRecognizer;
    private Speaker mSpeaker;
    private WakeupListener mWakeupListener;
    private RecognitionListener mRecognitionListener;
    private RawDataListener mRawDataListener;
    private TtsListener mTtsListener;
    private GrammarConstraint mTwoSlotGrammar;
    private GrammarConstraint mThreeSlotGrammar;
    private VoiceHandler mHandler = new VoiceHandler(this);
    private Base mBase;
    private Timer mTimer;


    public static class VoiceHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        private VoiceHandler(MainActivity instance) {
            mActivity = new WeakReference<>(instance);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity mainActivity = mActivity.get();
            if (mainActivity != null) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case SHOW_MSG:
                        mainActivity.showMessage((String) msg.obj, msg.arg1);
                        break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        //Initialize
        msg = (TextView) findViewById(R.id.textView_msg);
        status = (TextView) findViewById(R.id.textView_status);
        msg.setMovementMethod(new ScrollingMovementMethod());
        server = new Server(this);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        Log.d("hello123", "hello kic");
        mSpeaker = Speaker.getInstance();
        initButtons();
        initListeners();

        //Bind automatically
        mBindButton.performClick();
        mBase = Base.getInstance();
        mBase.bindService(getApplicationContext(), new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                mTimer = new Timer();
                mTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        final AngularVelocity av = mBase.getAngularVelocity();
                        final LinearVelocity lv = mBase.getLinearVelocity();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                            }
                        });
                    }
                }, 50, 200);
            }

            @Override
            public void onUnbind(String reason) {
                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer = null;
                }
            }
        });


        //test
        msg.append(server.getIpAddress() + ":" + server.getPort());
//        while (true){
//            if (mStatusTextView.getText().toString() != null){
//                try {
//                    mSpeaker.speak(status.getText().toString(), mTtsListener);
//                    mStatusTextView.setText("");
//                }catch (VoiceException e) {
//                    Log.w(TAG, "Exception: ", e);
//                }
//            }
//        }

    }

    // init UI.
    private void initButtons() {
        mBindButton = (Button) findViewById(R.id.button_bind);
        mUnbindButton = (Button) findViewById(R.id.button_unbind);
        mSpeakButton = (Button) findViewById(R.id.button_speak);
        mStopSpeakButton = (Button) findViewById(R.id.button_stop_speaking);
        mStatusTextView = (TextView) findViewById(R.id.textView_status);

        mBindButton.setOnClickListener(this);
        mUnbindButton.setOnClickListener(this);
        mSpeakButton.setOnClickListener(this);
        mStopSpeakButton.setOnClickListener(this);
    }

    //init listeners.
    private void initListeners() {

        mRecognitionBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0,
                        getString(R.string.recognition_connected));
                mHandler.sendMessage(connectMsg);
            }

            @Override
            public void onUnbind(String s) {
                //speaker service or recognition service unbind, disable function buttons.
                disableSampleFunctionButtons();
                mUnbindButton.setEnabled(false);
                Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0, getString(R.string.recognition_disconnected));
                mHandler.sendMessage(connectMsg);
            }
        };

        mSpeakerBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                try {
                    Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0,
                            getString(R.string.speaker_connected));
                    mHandler.sendMessage(connectMsg);
                    //get speaker service language.
                    mSpeakerLanguage = mSpeaker.getLanguage();
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                bindSpeakerService = true;
            }

            @Override
            public void onUnbind(String s) {
                //speaker service or recognition service unbind, disable function buttons.
                disableSampleFunctionButtons();
                Message connectMsg = mHandler.obtainMessage(SHOW_MSG, APPEND, 0, getString(R.string.speaker_disconnected));
                mHandler.sendMessage(connectMsg);
            }
        };

        mRawDataListener = new RawDataListener() {
            @Override
            public void onRawData(byte[] bytes, int i) {
                createFile(bytes, "raw.pcm");
            }
        };

        mTtsListener = new TtsListener() {
            @Override
            public void onSpeechStarted(String s) {
                //s is speech content, callback this method when speech is starting.
                Log.d(TAG, "onSpeechStarted() called with: s = [" + s + "]");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "speech start");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public void onSpeechFinished(String s) {
                //s is speech content, callback this method when speech is finish.
                Log.d(TAG, "onSpeechFinished() called with: s = [" + s + "]");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "speech end");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public void onSpeechError(String s, String s1) {
                //s is speech content, callback this method when speech occurs error.
                Log.d(TAG, "onSpeechError() called with: s = [" + s + "], s1 = [" + s1 + "]");
                Message statusMsg = mHandler.obtainMessage(SHOW_MSG, CLEAR, 0, "speech error: " + s1);
                mHandler.sendMessage(statusMsg);
            }
        };
    }
    //enable sample function buttons.
    private void enableSampleFunctionButtons() {
        mSpeakButton.setEnabled(true);
        mStopSpeakButton.setEnabled(true);
    }

    //disable sample function buttons.
    private void disableSampleFunctionButtons() {
        mUnbindButton.setEnabled(false);
        mSpeakButton.setEnabled(false);
        mStopSpeakButton.setEnabled(false);
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_bind:

                //bind the speaker service.
                mSpeaker.bindService(MainActivity.this, mSpeakerBindStateListener);
                break;
            case R.id.button_unbind:
                mSpeaker.unbindService();
                disableSampleFunctionButtons();
                mBindButton.setEnabled(true);
                break;
            case R.id.button_speak:
                showTip("start to speak.");
                Log.d(TAG, "onClick speak");
                //tts
                /*try {
                    Log.d(TAG, "stopSpeak");
                    mSpeaker.stopSpeak();
                } catch (VoiceException | RemoteException e) {
                    Log.e(TAG, "Exception: ", e);
                }*/
                try {
                    if (mSpeakerLanguage == Languages.EN_US) {
                        Log.d(TAG, "start speak");

                        mSpeaker.speak(status.getText().toString(), mTtsListener);
                        //mStatusTextView.setText("");
                        String message=status.getText().toString();
                        Log.d(TAG, message);
                        if(message.equals("move_forward")) {
                            Log.d("hello", "call me crazy");

                            mBase.setLinearVelocity(1.0f);
                            mBase.setAngularVelocity(0);

                        }
                        else if(message.equals("move_backward")) {
                            mBase.setLinearVelocity(-1.0f);
                            mBase.setAngularVelocity(0);
                        }
                        else if (message.equals("move_stop")) {
                            mBase.setAngularVelocity(0);
                            mBase.setLinearVelocity(0);
                        }
                        else if (message.equals("move_right"))
                        {
                            mBase.setAngularVelocity(-0.90f);
                        }
                        else if (message.equals("move_left")){
                            mBase.setAngularVelocity(0.90f);
                        }



                        Log.d(TAG, status.getText().toString());
                    } else if (mSpeakerLanguage == Languages.ZH_CN) {
                        mSpeaker.speak("你好，我是赛格威机器人。", mTtsListener);
                    } else {
                        Log.e(TAG, "It should not happen!");
                        break;
                    }
                    //block for 3 seconds, return true if speech time is smaller than 3 seconds, else return false.
                    /*boolean timeout = mSpeaker.waitForSpeakFinish(3000);*/
                } catch (VoiceException e) {
                    Log.w(TAG, "Exception: ", e);
                }
                break;
            case R.id.button_stop_speaking:
                showTip("stop speaking.");
                //stop speech.
                try {
                    mSpeaker.stopSpeak();
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
                break;

        }
    }
    private void showMessage(String msg, final int pattern) {
        switch (pattern) {
            case CLEAR:
                //mStatusTextView.setText(msg);
                break;
            case APPEND:
                //mStatusTextView.append(msg);
                break;
        }
    }

    private void createFile(byte[] buffer, String fileName) {
        RandomAccessFile randomFile = null;
        try {
            randomFile = new RandomAccessFile(FILE_PATH + fileName, "rw");
            long fileLength = randomFile.length();
            randomFile.seek(fileLength);
            randomFile.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (randomFile != null) {
                try {
                    randomFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void showTip(String tip) {
        Toast.makeText(this, tip, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (null != this.getCurrentFocus()) {
            InputMethodManager mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            return mInputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (mSpeaker != null) {
            mSpeaker = null;
        }
        super.onDestroy();
        server.onDestroy();
    }
}