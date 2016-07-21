/*
 * Copyright (c) 2015 Layer. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.layer.atlas;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.layer.atlas.messagetypes.AttachmentSender;
import com.layer.atlas.messagetypes.MessageSender;
import com.layer.atlas.messagetypes.text.TextSender;
import com.layer.atlas.provider.ParticipantProvider;
import com.layer.atlas.util.EditTextUtil;
import com.layer.atlas.util.OnSwipeTouchListener;
import com.layer.sdk.LayerClient;
import com.layer.sdk.listeners.LayerTypingIndicatorListener;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class AtlasMessageComposer extends FrameLayout {
    private EditText mMessageEditText;
    private Button mSendButton;
    private Button mAudioSendButton;
    private ImageView mAttachButton;
    private LinearLayout audioInfoView;
    private TextView recordTimeText;
    private ImageView recordIcon;

    private LayerClient mLayerClient;
    private ParticipantProvider mParticipantProvider;
    private Conversation mConversation;

    private TextSender mTextSender;
    private ArrayList<AttachmentSender> mAttachmentSenders = new ArrayList<AttachmentSender>();
    private MessageSender.Callback mMessageSenderCallback;

    private PopupWindow mAttachmentMenu;
    Context mContext;

    private long startTime = 0L;
    long timeInMilliseconds = 0L;
    long timeSwapBuff = 0L;
    long updatedTime = 0L;
    private Timer timer;

    private boolean mEnabled;
    private int mTextColor;
    private float mTextSize;
    private Typeface mTypeFace;
    private int mTextStyle;
    private int mUnderlineColor;
    private int mCursorColor;
    Handler durationHandler;
    Animation blinkAnimation;

    private MediaRecorder myAudioRecorder;
    private String outputFile = null;
    private String audioFileName;

    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;
    Activity activity;


    public AtlasMessageComposer(Context context) {
        super(context);
        this.mContext = context;
        initAttachmentMenu(context, null, 0);
        durationHandler = new Handler();
    }

    public AtlasMessageComposer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AtlasMessageComposer(Context context, AttributeSet attrs, int defStyle) {

        super(context, attrs, defStyle);
        this.mContext = context;
        parseStyle(context, attrs, defStyle);
        initAttachmentMenu(context, attrs, defStyle);
        durationHandler = new Handler();
    }

    /**
     * Prepares this AtlasMessageComposer for use.
     *
     * @return this AtlasMessageComposer.
     */
    public AtlasMessageComposer init(LayerClient layerClient, ParticipantProvider participantProvider) {
        LayoutInflater.from(getContext()).inflate(R.layout.atlas_message_composer, this);

        mLayerClient = layerClient;
        mParticipantProvider = participantProvider;

        mAttachButton = (ImageView) findViewById(R.id.attachment);
        mAttachButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                LinearLayout menu = (LinearLayout) mAttachmentMenu.getContentView();
                menu.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                mAttachmentMenu.showAsDropDown(v, 0, -menu.getMeasuredHeight() - v.getHeight());
            }
        });

        mMessageEditText = (EditText) findViewById(R.id.message_edit_text);
        audioInfoView = (LinearLayout) findViewById(R.id.audio_info_view);
        recordTimeText = (TextView) findViewById(R.id.audio_timer);
        recordIcon = (ImageView) findViewById(R.id.record_icon);

        audioInfoView.setOnTouchListener(new OnSwipeTouchListener(mContext) {
            public void onSwipeTop() {
            }
            public void onSwipeRight() {
            }
            public void onSwipeLeft() {
                audioInfoView.setVisibility(View.INVISIBLE);
                mAudioSendButton.setBackgroundResource(R.drawable.mic_icon);
                stopRecord();
            }
            public void onSwipeBottom() {
            }

        });

        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mConversation == null || mConversation.isDeleted()) return;
                if (s.length() > 0) {
                    mSendButton.setEnabled(isEnabled());
                    mConversation.send(LayerTypingIndicatorListener.TypingIndicator.STARTED);
                } else {
                    mSendButton.setEnabled(false);
                    mConversation.send(LayerTypingIndicatorListener.TypingIndicator.FINISHED);
                }
            }
        });

        mSendButton = (Button) findViewById(R.id.send_button);
        mAudioSendButton = (Button) findViewById(R.id.audio_button);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (!mTextSender.requestSend(mMessageEditText.getText().toString())) return;
                mMessageEditText.setText("");
                mSendButton.setEnabled(false);
            }
        });

        mAudioSendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String mode = "";
                if(view.getTag() != null){
                    mode = (String) view.getTag();
                }

                if(mode.equalsIgnoreCase("record_mode")){
                    stopRecord();
                    sendAudioFileToLayer();
                    audioInfoView.setVisibility(View.INVISIBLE);
                    mAudioSendButton.setBackgroundResource(R.drawable.mic_icon);
                } else {
                    Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(150);
                    audioInfoView.setVisibility(View.VISIBLE);
                    mAudioSendButton.setBackgroundResource(R.drawable.send_icon);

                    startRecord();

                    view.setTag("record_mode");
                }

            }
        });

        blinkAnimation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
        blinkAnimation.setDuration(500); // duration - half a second
        blinkAnimation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
        blinkAnimation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
        blinkAnimation.setRepeatMode(Animation.REVERSE); // Reverse animation at the end so the button will fade back in

        //create Directory If Not Exists
        createDirectoryIfNotExists("/Chat/Audio/Sent");

        applyStyle();
        return this;
    }

    private void startRecord(){
        recordIcon.startAnimation(blinkAnimation);

        long time = System.currentTimeMillis();
        audioFileName = String.valueOf(time);

        if(createDirectoryIfNotExists("/Chat/Audio/Sent")){
            outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Chat/Audio/Sent/"+audioFileName+".mp4";
            myAudioRecorder = new MediaRecorder();
            myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            myAudioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
            myAudioRecorder.setOutputFile(outputFile);
            try {
                myAudioRecorder.prepare();
            } catch(IOException exception) {
                myAudioRecorder.reset();
                myAudioRecorder.release();
                myAudioRecorder = null;
                return;
            }
            myAudioRecorder.start();

            //start timer and update in composer
            startTime = SystemClock.uptimeMillis();
            timer = new Timer();
            MyTimerTask myTimerTask = new MyTimerTask();
            timer.schedule(myTimerTask, 1000, 1000);
        } else {
            Toast.makeText(mContext, "Unabel to create directory in sd card", Toast.LENGTH_SHORT).show();
        }

    }

    private void sendAudioMessage(int duration, String audioFileName){
        MessagePart[] parts = new MessagePart[2];
        File file = new File(outputFile);
        MessagePart full = null;
        try {
            full = mLayerClient.newMessagePart("audio/mp4", new FileInputStream(file), file.length());

            String mType = "mp4";
            String durationString = String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(duration),
                    TimeUnit.MILLISECONDS.toSeconds(duration) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
            );
            String intoString = "{\"format\":\"" + mType + "\", \"duration\":\"" + durationString + "\", \"fileName\":\"" + audioFileName + "\"}";
            MessagePart info = mLayerClient.newMessagePart("application/json+audioSize", intoString.getBytes());
            parts[0] = full;
            parts[1] = info;

            Message message = mLayerClient.newMessage(parts);
            String myName = mParticipantProvider.getParticipant(mLayerClient.getAuthenticatedUserId()).getName();
            message.getOptions().pushNotificationMessage(getContext().getString(R.string.atlas_notification_image, myName));
            mConversation.send(message);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecord(){
        if (timer != null) {
            timer.cancel();
        }
        blinkAnimation.cancel();
        recordTimeText.setText("00:00");
        mAudioSendButton.setTag("");

        //stop and release the media recorder
        if(myAudioRecorder != null){
            myAudioRecorder.stop();
            myAudioRecorder.release();
            myAudioRecorder = null;
        }
    }

    private void sendAudioFileToLayer(){
        //get duration of audio and send message to layer
        MediaPlayer mp = MediaPlayer.create(mContext, Uri.parse(outputFile));
        int duration = mp.getDuration();
        Log.d("@@##", "MediaRecorder sendAudioFileToLayer duration = "+duration);
        sendAudioMessage(duration, audioFileName);
    }

    private static boolean createDirectoryIfNotExists(String path) {
        boolean result = true;

        File file = new File(Environment.getExternalStorageDirectory(), path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                result = false;
            }
        }
        return result;
    }

    class MyTimerTask extends TimerTask {

        @Override
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            final String hms = String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(updatedTime)
                            - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
                            .toHours(updatedTime)),
                    TimeUnit.MILLISECONDS.toSeconds(updatedTime)
                            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                            .toMinutes(updatedTime)));

            Runnable updateTimerText = new Runnable() {
                public void run() {
                    try {
                        if (recordTimeText != null)
                            recordTimeText.setText(hms);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }
            };

            durationHandler.postDelayed(updateTimerText, 10);
        }
    }

    /**
     * Sets the Conversation used for sending Messages.
     *
     * @param conversation the Conversation used for sending Messages.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setConversation(Conversation conversation) {
        mConversation = conversation;
        if (mTextSender != null) mTextSender.setConversation(conversation);
        for (AttachmentSender sender : mAttachmentSenders) {
            sender.setConversation(conversation);
        }
        return this;
    }

    /**
     * Sets a listener for receiving the message EditText focus change callbacks.
     *
     * @param listener Listener for receiving the message EditText focus change callbacks.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setOnMessageEditTextFocusChangeListener(OnFocusChangeListener listener) {
        mMessageEditText.setOnFocusChangeListener(listener);
        return this;
    }

    /**
     * Sets the TextSender used for sending composed text messages.
     *
     * @param textSender TextSender used for sending composed text messages.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setTextSender(TextSender textSender) {
        mTextSender = textSender;
        mTextSender.init(this.getContext().getApplicationContext(), mLayerClient, mParticipantProvider);
        mTextSender.setConversation(mConversation);
        if (mMessageSenderCallback != null) mTextSender.setCallback(mMessageSenderCallback);
        return this;
    }

    /**
     * Adds AttachmentSenders to this AtlasMessageComposer's attachment menu.
     *
     * @param senders AttachmentSenders to add to this AtlasMessageComposer's attachment menu.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer addAttachmentSenders(AttachmentSender... senders) {
        for (AttachmentSender sender : senders) {
            if (sender.getTitle() == null && sender.getIcon() == null) {
                throw new NullPointerException("Attachment handlers must have at least a title or icon specified.");
            }
            sender.init(this.getContext().getApplicationContext(), mLayerClient, mParticipantProvider);
            sender.setConversation(mConversation);
            if (mMessageSenderCallback != null) sender.setCallback(mMessageSenderCallback);
            mAttachmentSenders.add(sender);
            addAttachmentMenuItem(sender);
        }
        if (!mAttachmentSenders.isEmpty()) mAttachButton.setVisibility(View.VISIBLE);
        return this;
    }

    /**
     * Sets an optional callback for receiving MessageSender events.  If non-null, overrides any
     * callbacks already set on MessageSenders.
     *
     * @param callback Callback to receive MessageSender events.
     * @return This AtlasMessageComposer.
     */
    public AtlasMessageComposer setMessageSenderCallback(MessageSender.Callback callback) {
        mMessageSenderCallback = callback;
        if (mMessageSenderCallback == null) return this;
        if (mTextSender != null) mTextSender.setCallback(callback);
        for (AttachmentSender sender : mAttachmentSenders) {
            sender.setCallback(callback);
        }
        return this;
    }

    public AtlasMessageComposer setTypeface(Typeface typeface) {
        this.mTypeFace = typeface;
        applyTypeface();
        return this;
    }

    /**
     * Must be called from Activity's onActivityResult to allow attachment senders to manage results
     * from e.g. selecting a gallery photo or taking a camera image.
     *
     * @param activity    Activity receiving the result.
     * @param requestCode Request code from the Activity's onActivityResult.
     * @param resultCode  Result code from the Activity's onActivityResult.
     * @param data        Intent data from the Activity's onActivityResult.
     * @return this AtlasMessageComposer.
     */
    public AtlasMessageComposer onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        for (AttachmentSender sender : mAttachmentSenders) {
            sender.onActivityResult(activity, requestCode, resultCode, data);
        }
        return this;
    }

    /**
     * Must be called from Activity's onRequestPermissionsResult to allow attachment senders to
     * manage dynamic permisttions.
     *
     * @param requestCode  The request code passed in requestPermissions(android.app.Activity, String[], int)
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for (AttachmentSender sender : mAttachmentSenders) {
            sender.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mAttachButton != null) mAttachButton.setEnabled(enabled);
        if (mMessageEditText != null) mMessageEditText.setEnabled(enabled);
        if (mSendButton != null) {
            mSendButton.setEnabled(enabled && (mMessageEditText != null) && (mMessageEditText.getText().length() > 0));
        }
        super.setEnabled(enabled);
    }

    private void parseStyle(Context context, AttributeSet attrs, int defStyle) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AtlasMessageComposer, R.attr.AtlasMessageComposer, defStyle);
        mEnabled = ta.getBoolean(R.styleable.AtlasMessageComposer_android_enabled, true);
        this.mTextColor = ta.getColor(R.styleable.AtlasMessageComposer_inputTextColor, context.getResources().getColor(R.color.atlas_text_black));
        this.mTextSize = ta.getDimensionPixelSize(R.styleable.AtlasMessageComposer_inputTextSize, context.getResources().getDimensionPixelSize(R.dimen.atlas_text_size_input));
        this.mTextStyle = ta.getInt(R.styleable.AtlasMessageComposer_inputTextStyle, Typeface.NORMAL);
        String typeFaceName = ta.getString(R.styleable.AtlasMessageComposer_inputTextTypeface);
        this.mTypeFace = typeFaceName != null ? Typeface.create(typeFaceName, mTextStyle) : null;
        this.mUnderlineColor = ta.getColor(R.styleable.AtlasMessageComposer_inputUnderlineColor, context.getResources().getColor(R.color.atlas_color_primary_blue));
        this.mCursorColor = ta.getColor(R.styleable.AtlasMessageComposer_inputCursorColor, context.getResources().getColor(R.color.atlas_color_primary_blue));
        ta.recycle();
    }

    private void applyStyle() {
        setEnabled(mEnabled);

        mMessageEditText.setTextColor(mTextColor);
        mMessageEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        EditTextUtil.setCursorDrawableColor(mMessageEditText, mCursorColor);
        EditTextUtil.setUnderlineColor(mMessageEditText, mUnderlineColor);
        applyTypeface();

        ColorStateList list = getResources().getColorStateList(R.color.atlas_message_composer_attach_button);
        Drawable d = DrawableCompat.wrap(mAttachButton.getDrawable().mutate());
        DrawableCompat.setTintList(d, list);
        mAttachButton.setImageDrawable(d);
    }

    private void applyTypeface() {
        mMessageEditText.setTypeface(mTypeFace, mTextStyle);
    }

    private void addAttachmentMenuItem(AttachmentSender sender) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        LinearLayout menuLayout = (LinearLayout) mAttachmentMenu.getContentView();

        View menuItem = inflater.inflate(R.layout.atlas_message_composer_attachment_menu_item, menuLayout, false);
        ((TextView) menuItem.findViewById(R.id.title)).setText(sender.getTitle());
        menuItem.setTag(sender);
        menuItem.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mAttachmentMenu.dismiss();
                ((AttachmentSender) v.getTag()).requestSend();
            }
        });
        if (sender.getIcon() != null) {
            ImageView iconView = ((ImageView) menuItem.findViewById(R.id.icon));
            iconView.setImageResource(sender.getIcon());
            iconView.setVisibility(VISIBLE);
            Drawable d = DrawableCompat.wrap(iconView.getDrawable());
            DrawableCompat.setTint(d, getResources().getColor(R.color.atlas_icon_enabled));
        }
        menuLayout.addView(menuItem);
    }

    private void initAttachmentMenu(Context context, AttributeSet attrs, int defStyle) {
        if (mAttachmentMenu != null) throw new IllegalStateException("Already initialized menu");

        if (attrs == null) {
            mAttachmentMenu = new PopupWindow(context);
        } else {
            mAttachmentMenu = new PopupWindow(context, attrs, defStyle);
        }
        mAttachmentMenu.setContentView(LayoutInflater.from(context).inflate(R.layout.atlas_message_composer_attachment_menu, null));
        mAttachmentMenu.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mAttachmentMenu.setOutsideTouchable(true);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        if (mAttachmentSenders.isEmpty()) return superState;
        SavedState savedState = new SavedState(superState);
        for (AttachmentSender sender : mAttachmentSenders) {
            Parcelable parcelable = sender.onSaveInstanceState();
            if (parcelable == null) continue;
            savedState.put(sender.getClass(), parcelable);
        }
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        for (AttachmentSender sender : mAttachmentSenders) {
            Parcelable parcelable = savedState.get(sender.getClass());
            if (parcelable == null) continue;
            sender.onRestoreInstanceState(parcelable);
        }
    }

    /**
     * Saves a map from AttachmentSender class to AttachmentSender saved instance.
     */
    private static class SavedState extends BaseSavedState {
        Bundle mBundle = new Bundle();

        public SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState put(Class<? extends AttachmentSender> cls, Parcelable parcelable) {
            mBundle.putParcelable(cls.getName(), parcelable);
            return this;
        }

        Parcelable get(Class<? extends AttachmentSender> cls) {
            return mBundle.getParcelable(cls.getName());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeBundle(mBundle);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        private SavedState(Parcel in) {
            super(in);
            mBundle = in.readBundle();
        }
    }
}
