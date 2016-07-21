package com.layer.atlas.messagetypes.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.layer.atlas.R;
import com.layer.atlas.messagetypes.AtlasCellFactory;
import com.layer.atlas.provider.ParticipantProvider;
import com.layer.atlas.util.Log;
import com.layer.sdk.LayerClient;
import com.layer.sdk.listeners.LayerProgressListener;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Mime handles all MIME Types by simply displaying all MessagePart MIME Types as text.
 */
public class AudioCellFactory extends AtlasCellFactory<AudioCellFactory.CellHolder, AudioCellFactory.ParsedContent> implements View.OnClickListener, SeekBar.OnSeekBarChangeListener{
    private Context mContext = null;
    private MediaPlayer mMediaPlayer;
    private String previousFileName = null;
    private AudioMessageRow previousMessageRow = null;
    private double mediaPlayerTimeElapsed = 0, mediaPlayerFinalTime = 0;
    private Handler durationHandler = new Handler();
    AudioMessageRow aMessageRow = null;
    LayerClient layerClient;

    public AudioCellFactory() {
        super(256 * 1024);
    }


    private void playAudio(View v){
        String audioFilePath, currentFileName;
        aMessageRow = (AudioMessageRow) v.getTag();
        currentFileName = String.valueOf(aMessageRow.getParsedContent().getFileName());

        if(currentFileName.equalsIgnoreCase(previousFileName)){
            if(mMediaPlayer != null && mMediaPlayer.isPlaying() && !aMessageRow.getPaused()){
                mMediaPlayer.pause();
                aMessageRow.setPlayerPosition(mMediaPlayer.getCurrentPosition());
                aMessageRow.setPaused(true);
                aMessageRow.cellHolder.mImageView.setImageResource(R.drawable.play_icon);
                aMessageRow.cellHolder.mProgressBar.setVisibility(View.INVISIBLE);
                aMessageRow.cellHolder.mSeekbar.setVisibility(View.VISIBLE);

                return;
            } else if (mMediaPlayer != null && aMessageRow.getPaused()){
                mMediaPlayer.seekTo(aMessageRow.getPlayerPosition());
                mMediaPlayer.start();

                aMessageRow.cellHolder.mImageView.setImageResource(R.drawable.pause_icon);
                aMessageRow.cellHolder.mProgressBar.setVisibility(View.INVISIBLE);
                aMessageRow.cellHolder.mSeekbar.setVisibility(View.VISIBLE);

                return;
            }
        }

        //Log.d("@@##", "MediaRecorder checkIsMe ### getMessageSenderId = "+aMessageRow.parsedContent.getMessageSenderId()+", getAuthenticatedUserId = "+layerClient.getAuthenticatedUserId());
        //Log.d("@@##", "MediaRecorder checkIsMe = "+(aMessageRow.parsedContent.getMessageSenderId().equalsIgnoreCase(layerClient.getAuthenticatedUserId())));
        if(aMessageRow.parsedContent.getMessageSenderId().equalsIgnoreCase(layerClient.getAuthenticatedUserId())){
            audioFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Chat/Audio/Sent/"+currentFileName+"."+aMessageRow.parsedContent.getFormat();
        } else {
            audioFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Chat/Audio/"+currentFileName+"."+aMessageRow.parsedContent.getFormat();
        }

        File selectedAudioFile = new File(audioFilePath);
        if(selectedAudioFile.exists()){
            aMessageRow.cellHolder.mImageView.setImageResource(R.drawable.pause_icon);
            aMessageRow.cellHolder.mProgressBar.setVisibility(View.INVISIBLE);
            aMessageRow.cellHolder.mSeekbar.setVisibility(View.VISIBLE);

            if(mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }

            //Log.d("@@##", "MediaRecorder audioFilePath = "+audioFilePath);
            try{
                durationHandler = new Handler();

                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setDataSource(audioFilePath);
                mMediaPlayer.prepare();
                mediaPlayerFinalTime = mMediaPlayer.getDuration();
                aMessageRow.cellHolder.mSeekbar.setMax((int) mediaPlayerFinalTime);
                aMessageRow.cellHolder.mSeekbar.setOnSeekBarChangeListener(this);
                //aMessageRow.cellHolder.mSeekbar.setEnabled(false);
                mMediaPlayer.start();

                resetPreviousMediaPlayer();
                previousFileName = currentFileName;
                previousMessageRow = aMessageRow;

                mediaPlayerTimeElapsed = mMediaPlayer.getCurrentPosition();
                aMessageRow.cellHolder.mSeekbar.setProgress((int) mediaPlayerTimeElapsed);
                durationHandler.postDelayed(updateSeekBarTime, 100);
            }catch(Exception e){
                e.printStackTrace();
            }

            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
                @Override
                public void onCompletion(MediaPlayer mediaPlayer){
                    //Log.d("@@##", "MediaRecorder onCompletion Listener");
                    durationHandler.removeCallbacks(updateSeekBarTime);
                    resetPlayer(previousMessageRow);
                }
            });
        } else {
            Toast.makeText(mContext, "File not found", Toast.LENGTH_SHORT).show();
        }

    }

    //handler to change seekBarTime
    private Runnable updateSeekBarTime = new Runnable() {
        public void run() {
            //get current position
            mediaPlayerTimeElapsed = mMediaPlayer.getCurrentPosition();
            //set seekbar progress
            aMessageRow.cellHolder.mSeekbar.setProgress((int) mediaPlayerTimeElapsed);
            //set time remaing
            double timeRemaining = mediaPlayerFinalTime - mediaPlayerTimeElapsed;
            String durationString = String.format("%02d:%02d",   TimeUnit.MILLISECONDS.toMinutes((long) mediaPlayerTimeElapsed),
                    TimeUnit.MILLISECONDS.toSeconds((long) mediaPlayerTimeElapsed) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long) mediaPlayerTimeElapsed))
            );
            aMessageRow.cellHolder.mTextView.setText(durationString);
            //repeat yourself that again in 100 miliseconds
            durationHandler.postDelayed(this, 100);
        }
    };

    private void resetPreviousMediaPlayer(){
        if(previousMessageRow != null && !previousMessageRow.equals(aMessageRow)){
            //Log.d("@@##", "resetPlayer previousMessageRow");
            resetPlayer(previousMessageRow);
        } else {
            //Log.d("@@##", "previousMessageRow is NULL");
        }
    }

    private void resetPlayer(AudioMessageRow messageRow){
        messageRow.cellHolder.mImageView.setImageResource(R.drawable.play_icon);
        messageRow.cellHolder.mSeekbar.setProgress(0);
        messageRow.cellHolder.mTextView.setText(aMessageRow.getParsedContent().getDuration());

        messageRow.setPlayerPosition(0);
        messageRow.setPaused(false);
    }

    @Override
    public boolean isBindable(Message message) {
        return true;
    }

    @Override
    public CellHolder createCellHolder(ViewGroup cellView, boolean isMe, LayoutInflater layoutInflater) {
        Context context = cellView.getContext();
        mMediaPlayer = new MediaPlayer();
        this.mContext = cellView.getContext();

        View v = layoutInflater.inflate(R.layout.atlas_message_item_cell_audio, cellView, true);
        v.setBackgroundResource(isMe ? R.drawable.atlas_message_item_cell_them : R.drawable.atlas_message_item_cell_them);

        TextView t = (TextView) v.findViewById(R.id.cell_text);
        t.setTextColor(context.getResources().getColor(isMe ? R.color.atlas_text_black : R.color.atlas_text_black));
        return new CellHolder(v);
    }

    @Override
    public ParsedContent parseContent(LayerClient layerClient, ParticipantProvider participantProvider, Message message) {
        //audioMessage = message;
        this.layerClient = layerClient;
        //Log.d("@@##", "MediaRecorder getSender.getUserId = "+message.getSender().getUserId());
        //Log.d("@@##", "MediaRecorder getAuthenticatedUserId = "+layerClient.getAuthenticatedUserId());
        String duration = "duration ";
        String opFileName = "";
        String opFormat = "mp4";
        try {

            JSONObject infoObject = new JSONObject(new String(message.getMessageParts().get(1).getData()));
            //Log.d("@@##", "MediaRecorder infoObject = "+infoObject.toString());
            if(infoObject.has("duration")){
                duration += infoObject.getString("duration");
            } else {
                duration += "not available";
            }
            if(infoObject.has("fileName")){
                opFileName = infoObject.getString("fileName");
            }
            if(infoObject.has("format")){
                opFormat = infoObject.getString("format");
            }

            //Log.d("@@##", "MediaRecorder parsed format = "+opFormat);
        } catch (JSONException e) {
            if (Log.isLoggable(Log.ERROR)) {
                Log.e(e.getMessage(), e);
            }
        }
        return new ParsedContent(opFormat, duration, opFileName, message.getSender().getUserId());
    }


    @Override
    public void bindCellHolder(CellHolder cellHolder, ParsedContent parsedContent, Message message, CellHolderSpecs specs) {
        AudioMessageRow audioMessageRow = new AudioMessageRow(parsedContent, cellHolder, message);
        cellHolder.mImageView.setTag(audioMessageRow);
        cellHolder.mTextView.setText(audioMessageRow.getParsedContent().getDuration());
        cellHolder.mImageView.setOnClickListener(this);

        //Log.d("@@##", "MediaRecorder bindCellHolder getFileName = "+audioMessageRow.getParsedContent().getFileName());
        //Log.d("@@##", "MediaRecorder bindCellHolder message status = "+message.getMessageParts().get(0).getTransferStatus());

        if(message.getMessageParts().get(0).getTransferStatus() == MessagePart.TransferStatus.READY_FOR_DOWNLOAD){
            cellHolder.mImageView.setImageResource(R.drawable.audio_download_icon);
            cellHolder.mProgressBar.setVisibility(View.INVISIBLE);
            cellHolder.mSeekbar.setVisibility(View.VISIBLE);
        } else {
            cellHolder.mImageView.setImageResource(R.drawable.play_icon);
            cellHolder.mProgressBar.setVisibility(View.INVISIBLE);
            cellHolder.mSeekbar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        //Log.d("@@##", "MediaRecorder download/play audio");
        AudioMessageRow aMessageRow =(AudioMessageRow) v.getTag();
        List<MessagePart> parts = aMessageRow.getMessage().getMessageParts();
        if(parts.get(0).getTransferStatus() == MessagePart.TransferStatus.READY_FOR_DOWNLOAD){
            //Log.d("@@##", "MediaRecorder LETS DOWNLOAD = "+parts.get(0));
            aMessageRow.cellHolder.mProgressBar.setVisibility(View.VISIBLE);
            aMessageRow.cellHolder.mSeekbar.setVisibility(View.INVISIBLE);
            downloadMessagePart(parts.get(0), v);
        } else {
            //Log.d("@@##", "MediaRecorder trying to play audio from file system");
            playAudio(v);
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        try {
            if (mMediaPlayer.isPlaying() || mMediaPlayer != null) {
                if (fromUser){
                    mMediaPlayer.seekTo(progress);
                }
            } else if (mMediaPlayer == null) {
                Toast.makeText(mContext, "Media is not running",  Toast.LENGTH_SHORT).show();
                seekBar.setProgress(0);
            }
        } catch (Exception e) {
            seekBar.setEnabled(false);
        }
    }

    public class CellHolder extends AtlasCellFactory.CellHolder {
        TextView mTextView;
        ImageView mImageView;
        ProgressBar mProgressBar;
        SeekBar mSeekbar;

        public CellHolder(View view) {
            mTextView = (TextView) view.findViewById(R.id.cell_text);
            mImageView = (ImageView) view.findViewById(R.id.cell_image);
            mProgressBar = (ProgressBar) view.findViewById(R.id.downloadProgressBar);
            mSeekbar = (SeekBar) view.findViewById(R.id.seekBar);
            mSeekbar.setClickable(false);
        }
    }

    public class AudioMessageRow {
        private ParsedContent parsedContent;
        private String fileName;
        private String duration;
        private ImageView cellImage;
        private SeekBar seekBar;
        private ProgressBar progressBar;
        private CellHolder cellHolder;
        private boolean isPaused = false;
        private int playerPosition = 0;
        private Message message;

        public AudioMessageRow(ParsedContent parsedContent, CellHolder cellHolder, Message message){
            this.parsedContent = parsedContent;
            this.cellHolder = cellHolder;
            this.message = message;
        }

        public ParsedContent getParsedContent() {
            return parsedContent;
        }

        public void setParsedContent(ParsedContent parsedContent) {
            this.parsedContent = parsedContent;
        }

        public boolean getPaused(){
            return this.isPaused;
        }

        public void setPaused(boolean status){
            this.isPaused = status;
        }

        public int getPlayerPosition() {
            return playerPosition;
        }

        public void setPlayerPosition(int playerPosition) {
            this.playerPosition = playerPosition;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }
    }

    public static class ParsedContent implements AtlasCellFactory.ParsedContent {
        private final String mString;
        private final int mSize;
        private String mFormat;
        private String mDuraion;
        private String mFileName;
        private String messageSenderId;

        public ParsedContent(String string) {
            mString = string;
            mSize = mString.getBytes().length;
        }

        public ParsedContent(String format, String duraion, String fileName, String senderId) {
            mString = "";
            this.mSize = mString.getBytes().length;
            this.mFormat = format;
            this.mDuraion = duraion;
            this.mFileName = fileName;
            this.messageSenderId = senderId;
        }
        public String getFormat() {
            return mFormat;
        }

        public void setFormat(String format) {
            this.mFormat = format;
        }
        public String getDuration() {
            return mDuraion;
        }
        public String getFileName() {
            return mFileName;
        }

        public String getString() {
            return mString;
        }

        public String getMessageSenderId() {
            return messageSenderId;
        }

        public void setMessageSenderId(String messageSenderId) {
            this.messageSenderId = messageSenderId;
        }

        @Override
        public int sizeOf() {
            return mSize;
        }

        @Override
        public String toString() {
            return mString;
        }
    }

    public void downloadMessagePart(final MessagePart part, final View v){

        //You can add whatever conditions make sense. In this case, we only start the download if the
        // MessagePart is ready (not DOWNLOADING or COMPLETE)
        //Log.d("@@##", "MediaRecorder TransferStatus = "+part.getTransferStatus());
        //Log.d("@@##", "MediaRecorder condition = "+((part.getTransferStatus() == MessagePart.TransferStatus.READY_FOR_DOWNLOAD)));
        if((part.getTransferStatus() == MessagePart.TransferStatus.READY_FOR_DOWNLOAD)){
            //Start the download with a ProgressListener
            part.download(new LayerProgressListener() {

                @Override
                public void onProgressStart(MessagePart messagePart, Operation operation) {
                }

                @Override
                public void onProgressUpdate(MessagePart messagePart, Operation operation, long bytes) {
                    //You can calculate the percentage complete based on the size of the Message Part
                    double fraction = (double) bytes / (double) messagePart.getSize();
                    int progress = (int) Math.round(fraction * 100);

                    //Use this to update any GUI elements associated with this Message Part
                    System.out.println(operation.toString() + "MediaRecorder Percent Complete: " + progress);
                }

                @Override
                public void onProgressComplete(MessagePart messagePart, Operation operation) {

                    if(messagePart.getMimeType().indexOf("audio") > -1){
                        byte[] myData = messagePart.getData();
                        //Log.d("@@##", "MediaRecorder myData = "+messagePart);
                        try {
                            writeFile(myData, v, messagePart.getMimeType());
                        } catch (Exception e){
                            //Log.d("@@##", "e.getMessage() = "+e.getMessage());
                        }

                    } else {
                        Log.d("@@##", "MediaRecorder incorrect MIME type ");
                    }
                }

                @Override
                public void onProgressError(MessagePart messagePart, LayerProgressListener.Operation operation, Throwable throwable) {
                    Log.d("@@##", "MediaRecorder onProgressError = "+throwable);
                }
            });
        }
    }


    private void writeFile(byte[] data, View v, String mimeType) throws IOException {
        //Log.d("@@##", "MediaRecorder writeFile :: mimeType = "+mimeType);
        String fileFormat = "mp4";
        /*
        if(mimeType.equalsIgnoreCase("audio/mp4")){
            fileFormat = "mp4";
        }
        */

        String[] parts = mimeType.split("/");
        fileFormat = parts[1];
        //Log.d("@@##", "MediaRecorder fileFormat = "+fileFormat);

        AudioMessageRow aMessageRow = (AudioMessageRow) v.getTag();
        try {
            String outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Chat/Audio/"+String.valueOf(aMessageRow.getParsedContent().getFileName())+"."+fileFormat;
            File myFile = new File(outputFile);
            myFile.createNewFile();
            FileOutputStream out = new FileOutputStream(myFile);
            out.write(data);
            out.close();
            //Log.d("@@##", "MediaRecorder writeFile success");
            Toast.makeText(mContext,  "Done writing SD", Toast.LENGTH_SHORT).show();

            playAudio(v);
        } catch (Exception e) {
            //Log.d("@@##", "MediaRecorder writeFile failed");
            Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    //==============================================================================================
    // Static utilities
    //==============================================================================================

    public static boolean isType(Message message) {
        for (MessagePart part : message.getMessageParts()) {
            if (part.getMimeType().startsWith("audio/")) return true;
        }
        return false;
    }

    public static String getMessagePreview(Context context, Message message) {
        return context.getString(R.string.atlas_message_preview_audio);
    }
}
